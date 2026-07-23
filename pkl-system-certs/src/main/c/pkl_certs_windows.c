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
// pkl_certs_windows.c
#include <windows.h>
#include <wincrypt.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>

#include "org_pkl_certs_NativeCertificateLoader.h"

typedef struct {
	char *data;
	size_t length;
	size_t capacity;
} buffer_t;

static bool buffer_reserve(buffer_t *buf, size_t additional) {
	if (buf->length + additional <= buf->capacity) {
		return true;
	}
	size_t newCapacity = buf->capacity == 0 ? 4096 : buf->capacity;
	while (newCapacity < buf->length + additional) {
		newCapacity *= 2;
	}
	char *newData = realloc(buf->data, newCapacity);
	if (newData == NULL) {
		return false;
	}
	buf->data = newData;
	buf->capacity = newCapacity;
	return true;
}

static bool buffer_append(buffer_t *buf, const char *data, size_t length) {
	if (!buffer_reserve(buf, length)) {
		return false;
	}
	memcpy(buf->data + buf->length, data, length);
	buf->length += length;
	return true;
}

/**
 * Returns true if `cert` is explicitly distrusted, i.e. building a trust
 * chain for it reports CERT_TRUST_IS_EXPLICIT_DISTRUST (for example, because
 * it has been added to the "Disallowed" certificate store).
 */
static bool is_cert_distrusted(PCCERT_CONTEXT cert) {
	CERT_CHAIN_PARA chainPara;
	memset(&chainPara, 0, sizeof(chainPara));
	chainPara.cbSize = sizeof(chainPara);

	PCCERT_CHAIN_CONTEXT chainContext = NULL;
	if (!CertGetCertificateChain(NULL, cert, NULL, NULL, &chainPara, 0, NULL,
			&chainContext)) {
		// If we can't build a chain to evaluate trust, don't treat that as
		// explicit distrust.
		return false;
	}

	bool distrusted = (chainContext->TrustStatus.dwErrorStatus
			& CERT_TRUST_IS_EXPLICIT_DISTRUST) != 0;
	CertFreeCertificateChain(chainContext);
	return distrusted;
}

/**
 * Appends the PEM encoding of `cert` to `buf`. Returns false on failure.
 */
static bool append_cert_as_pem(PCCERT_CONTEXT cert, buffer_t *buf) {
	DWORD pemLength = 0;
	if (!CryptBinaryToStringA(cert->pbCertEncoded, cert->cbCertEncoded,
			CRYPT_STRING_BASE64HEADER, NULL, &pemLength)) {
		return false;
	}

	// pemLength includes the trailing null terminator that
	// CryptBinaryToStringA writes; reserve space for it, but only count the
	// PEM text itself towards the buffer's length.
	if (!buffer_reserve(buf, pemLength)) {
		return false;
	}
	if (!CryptBinaryToStringA(cert->pbCertEncoded, cert->cbCertEncoded,
			CRYPT_STRING_BASE64HEADER, buf->data + buf->length, &pemLength)) {
		return false;
	}
	buf->length += pemLength - 1;
	return true;
}

int get_os_trusted_certs(char **out) {
	HCERTSTORE store = CertOpenSystemStoreW(0, L"ROOT");
	if (store == NULL) {
		return -1;
	}

	buffer_t buf = { 0 };
	PCCERT_CONTEXT cert = NULL;
	while ((cert = CertEnumCertificatesInStore(store, cert)) != NULL) {
		if (is_cert_distrusted(cert)) {
			continue;
		}
		if (!append_cert_as_pem(cert, &buf)) {
			CertFreeCertificateContext(cert);
			free(buf.data);
			CertCloseStore(store, 0);
			return -1;
		}
	}
	CertCloseStore(store, 0);

	if (!buffer_append(&buf, "\0", 1)) {
		free(buf.data);
		return -1;
	}

	*out = buf.data;
	return (int) buf.length - 1;
}

JNIEXPORT jbyteArray JNICALL Java_org_pkl_certs_NativeCertificateLoader_getBytes(
		JNIEnv *env, jobject thiz) {
	(void) thiz;
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
