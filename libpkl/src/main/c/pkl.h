/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
// pkl.h
#ifndef PKL_H
#define PKL_H

#if defined(__cplusplus)
extern "C" {
#endif

#define PKL_ERR_NOALLOC   1     /* Failed to allocate a value */
#define PKL_ERR_LOCK      2     /* Failed to craete a mutex, or acquire a lock on a mutex */

/**
 * Pkl executor instance that manages communication with the Pkl runtime.
 *
 * Instances should be created via pkl_init() and destroyed via pkl_close().
 *
 * All operations on this struct are considered thread-safe and are synchronized via a mutex.
 */
typedef struct __pkl_exec_t pkl_exec_t;

/**
 * The callback that gets called when a message is received from Pkl.
 *
 * Messages must be deserialized to Pkl's Message Passing API: 
 * https://pkl-lang.org/main/current/bindings-specification/message-passing-api.html
 *
 * @param length    The length the message bytes
 * @param message   The message itself
 * @param userData  User-defined data passed in from pkl_init.
 */
typedef void (*PklMessageResponseHandler)(int length, char *message, void *userData);

/**
 * Initialises and allocates a Pkl executor.
 * 
 * @param handler   The callback that gets called when a message is received from Pkl.
 * @param userData  User-defined data that gets passed to handler.
 *
 * @return NULL on failure, a pointer to a pkl_exec_t on success.
 */
pkl_exec_t *pkl_init(PklMessageResponseHandler handler, void *userData);

/**
 * Send a message to Pkl, providing the length and a pointer to the first byte.
 *
 * Messages must be serialized to Pkl's Message Passing API:
 * https://pkl-lang.org/main/current/bindings-specification/message-passing-api.html
 *
 * @param pexec     The Pkl executor instance.
 * @param length    The length of the message, in bytes.
 * @param message   The message to send to Pkl.
 *
 * @return 0 on success, -1 if `pexec` or `message` are NULL, and an error code otherwise.
 */
int pkl_send_message(pkl_exec_t *pexec, int length, char *message);

/**
 * Cleans up any resources that were created as part of the `pkl_init` process
 * for our `pkl_exec_t` instance.
 *
 * @param pexec     The Pkl executor instance.
 *
 * @return 0 on success, -1 if `pexec` is NULL, and an error code otherwise.
 */
int pkl_close(pkl_exec_t *pexec);

/**
 * Returns the version of Pkl in use.
 *
 * @return a string with the version information.
 */
const char* pkl_version();

/**
 * Returns a string description of a Pkl error code.
 */
const char* pkl_error_description(int error);

/**
 * Returns the error code of the last error.
 */
int pkl_get_last_error();

/**
 * Returns the last error message.
 */
const char *pkl_get_last_error_message();

#if defined(__cplusplus)
}
#endif
#endif
