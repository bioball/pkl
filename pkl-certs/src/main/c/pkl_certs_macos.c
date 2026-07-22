/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// pkl_certs_macos.c
#include <CoreFoundation/CoreFoundation.h>
#include <Security/Security.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

#include "org_pkl_certs_NativeCertificateLoader.h"

/**
 * Returns true if `cert` carries an explicit "deny" trust setting in the
 * system trust settings domain, i.e. it has been marked as distrusted even
 * though it is a built-in anchor certificate.
 */
static bool is_cert_distrusted(SecCertificateRef cert) {
	CFArrayRef trustSettings = NULL;
	OSStatus status = SecTrustSettingsCopyTrustSettings(cert,
			kSecTrustSettingsDomainSystem, &trustSettings);
	if (status != errSecSuccess || trustSettings == NULL) {
		// No trust settings entry (errSecItemNotFound) means the certificate
		// relies on its default anchor trust, i.e. it is not distrusted.
		return false;
	}

	bool distrusted = false;
	CFIndex count = CFArrayGetCount(trustSettings);
	for (CFIndex i = 0; i < count; i++) {
		CFDictionaryRef settings = (CFDictionaryRef) CFArrayGetValueAtIndex(
				trustSettings, i);
		CFNumberRef resultNumber = NULL;
		if (CFDictionaryGetValueIfPresent(settings, kSecTrustSettingsResult,
				(const void**) &resultNumber)) {
			int resultValue = kSecTrustSettingsResultTrustRoot;
			CFNumberGetValue(resultNumber, kCFNumberIntType, &resultValue);
			if (resultValue == kSecTrustSettingsResultDeny) {
				distrusted = true;
				break;
			}
		}
	}

	CFRelease(trustSettings);
	return distrusted;
}

int get_os_trusted_certs(char **out) {
	CFArrayRef anchorCerts = NULL;
	OSStatus status = SecTrustCopyAnchorCertificates(&anchorCerts);
	if (status != errSecSuccess || anchorCerts == NULL) {
		return -1;
	}

	CFIndex count = CFArrayGetCount(anchorCerts);
	CFMutableArrayRef trustedCerts = CFArrayCreateMutable(kCFAllocatorDefault,
			count, &kCFTypeArrayCallBacks);
	if (trustedCerts == NULL) {
		CFRelease(anchorCerts);
		return -1;
	}

	for (CFIndex i = 0; i < count; i++) {
		SecCertificateRef cert = (SecCertificateRef) CFArrayGetValueAtIndex(
				anchorCerts, i);
		if (!is_cert_distrusted(cert)) {
			CFArrayAppendValue(trustedCerts, cert);
		}
	}
	CFRelease(anchorCerts);

	CFDataRef pemData = NULL;
	status = SecItemExport(trustedCerts, kSecFormatPEMSequence,
			kSecItemPemArmour, NULL, &pemData);
	CFRelease(trustedCerts);
	if (status != errSecSuccess || pemData == NULL) {
		return -1;
	}

	CFIndex length = CFDataGetLength(pemData);
	char *buf = malloc((size_t) length + 1);
	if (buf == NULL) {
		CFRelease(pemData);
		return -1;
	}
	memcpy(buf, CFDataGetBytePtr(pemData), (size_t) length);
	buf[length] = '\0';
	CFRelease(pemData);

	*out = buf;
	return (int) length;
}

JNIEXPORT jbyteArray JNICALL Java_org_pkl_certs_NativeCertificateLoader_getBytes(
		JNIEnv *env, jobject thisObject) {
	(void) thisObject;
	char *certs = NULL;
	int length = get_os_trusted_certs(&certs);
	if (length < 0) {
		jclass exceptionClass = (*env)->FindClass(env,
				"java/lang/IllegalStateException");
		(*env)->ThrowNew(env, exceptionClass,
				"Failed to retrieve OS trusted certificates.");
		return NULL;
	}

	jbyteArray result = (*env)->NewByteArray(env, length);
	if (result != NULL) {
		(*env)->SetByteArrayRegion(env, result, 0, length,
				(const jbyte*) certs);
	}
	free(certs);
	return result;
}
