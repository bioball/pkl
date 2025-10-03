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
// pkl.c

#include <stdlib.h>
#include <stdio.h>

#ifdef _WIN32
#include <windows.h>
#else
#include <pthread.h>
#endif

#include <graal_isolate.h>
#include <libpkl_internal.h>

#include <pkl.h>

#ifndef PKL_VERSION
#define PKL_VERSION "0.0.0"
#endif

struct __pkl_exec_t {
#ifdef _WIN32
  CRITICAL_SECTION mutex;
#else
  pthread_mutex_t mutex;
#endif
  graal_isolatethread_t *graal_isolatethread;
};

static _Thread_local int pkl_last_error_code = 0;

static _Thread_local const char *pkl_last_error_message = NULL;

static void pkl_set_error(int code, const char *message) {
  pkl_last_error_code = code;
  pkl_last_error_message = message;
}

static void pkl_runtime_cleanup(pkl_exec_t *pexec) {
  pkl_internal_server_stop(pexec->graal_isolatethread);
  pkl_internal_close(pexec->graal_isolatethread);
  pexec->graal_isolatethread = NULL;
}

pkl_exec_t *pkl_init(PklMessageResponseHandler handler, void *userData) {
  pkl_exec_t *pexec = calloc(1, sizeof(pkl_exec_t));

  if (pexec == NULL) {
    pkl_set_error(PKL_ERR_NOALLOC, "Failed to allocate pkl_exec_t");
    return NULL;
  }

#ifdef _WIN32
  InitializeCriticalSection(&pexec->mutex);
  EnterCriticalSection(&pexec->mutex);
#else
  if (pthread_mutex_init(&pexec->mutex, NULL) != 0) {
    pkl_set_error(PKL_ERR_LOCK, "pkl_init: failed initialize mutex");
    free(pexec);
    return NULL;
  }

  if (pthread_mutex_lock(&pexec->mutex) != 0) {
    pkl_set_error(PKL_ERR_LOCK, "pkl_init: failed to lock mutex");
    pthread_mutex_destroy(&pexec->mutex);
    free(pexec);
    return NULL;
  }
#endif

  pexec->graal_isolatethread = pkl_internal_init();

  if (pexec->graal_isolatethread == NULL) {
    pkl_set_error(PKL_ERR_NOALLOC, "pkl_init: failed to allocate graal_isolatethread");
#ifdef _WIN32
    LeaveCriticalSection(&pexec->mutex);
    DeleteCriticalSection(&pexec->mutex);
#else
    pthread_mutex_unlock(&pexec->mutex);
    pthread_mutex_destroy(&pexec->mutex);
#endif
    free(pexec);
    return NULL;
  }

  pkl_internal_register_response_handler(pexec->graal_isolatethread, handler, userData);
  pkl_internal_server_start(pexec->graal_isolatethread);

#ifdef _WIN32
  LeaveCriticalSection(&pexec->mutex);
#else
  if (pthread_mutex_unlock(&pexec->mutex) != 0) {
    fprintf(stderr, "pkl_init: fatal: failed to unlock mutex.\n");
    abort();
  }
#endif

  return pexec;
};

int pkl_send_message(pkl_exec_t *pexec, int length, char *message) {
  if (pexec == NULL || message == NULL) {
    return -1;
  }

#ifdef _WIN32
  EnterCriticalSection(&pexec->mutex);
#else
  if (pthread_mutex_lock(&pexec->mutex) != 0) {
    pkl_set_error(PKL_ERR_LOCK, "pkl_send_message: failed to lock mutex");
    return PKL_ERR_LOCK;
  }
#endif

  pkl_internal_send_message(pexec->graal_isolatethread, length, message);

#ifdef _WIN32
  LeaveCriticalSection(&pexec->mutex);
#else
  if (pthread_mutex_unlock(&pexec->mutex) != 0) {
    fprintf(stderr, "pkl_send_message: fatal: failed to unlock mutex.\n");
    abort();
  }
#endif

  return 0;
};

int pkl_close(pkl_exec_t *pexec) {
  if (pexec == NULL) {
    return -1;
  }

#ifdef _WIN32
  EnterCriticalSection(&pexec->mutex);
#else
  if (pthread_mutex_lock(&pexec->mutex) != 0) {
    pkl_set_error(PKL_ERR_LOCK, "pkl_close: failed to lock mutex");
    return PKL_ERR_LOCK;
  }
#endif

  pkl_runtime_cleanup(pexec);

#ifdef _WIN32
  LeaveCriticalSection(&pexec->mutex);
  DeleteCriticalSection(&pexec->mutex);
#else
  if (pthread_mutex_unlock(&pexec->mutex) != 0) {
    fprintf(stderr, "pkl_close: fatal: failed to unlock mutex.\n");
    abort();
  }

  if (pthread_mutex_destroy(&pexec->mutex) != 0) {
    fprintf(stderr, "pkl_close: fatal: failed to destroy mutex.\n");
    abort();
  }
#endif

  free(pexec);

  return 0;
};

const char *pkl_version() {
  return PKL_VERSION;
}

const char *pkl_error_description(int error) {
  switch (error) {
    case PKL_ERR_NOALLOC: return "Memory allocation failed";
    case PKL_ERR_LOCK: return "Failed to lock, unlock, or destroy the mutex";
    default: return "Unknown error";
  }
}

const char *pkl_get_last_error_message() {
  return pkl_last_error_message;
}

int pkl_get_last_error() {
  return pkl_last_error_code;
}
