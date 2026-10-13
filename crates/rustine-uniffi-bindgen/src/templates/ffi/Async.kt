
internal const val UNIFFI_RUST_FUTURE_POLL_READY = 0.toByte()
internal const val UNIFFI_RUST_FUTURE_POLL_WAKE = 1.toByte()

internal val uniffiContinuationHandleMap = UniffiHandleMap<CancellableContinuation<Byte>>()

// FFI type for Rust future continuations
internal suspend fun<T, F, E: kotlin.Exception> uniffiRustCallAsync(
    rustFuture: Long,
    pollFunc: (Long, UniffiRustFutureContinuationCallback, Long) -> Unit,
    completeFunc: (Long, UniffiRustCallStatus) -> F,
    freeFunc: (Long) -> Unit,
    cancelFunc: (Long) -> Unit,
    liftFunc: (F) -> T,
    errorHandler: UniffiRustCallStatusErrorHandler<E>
): T {
    // Free the future outside `withContext`: its block never runs when the coroutine is cancelled
    // before it starts. `withContext` returns only after its block finished polling.
    try {
        return withContext(Dispatchers.IO) {
            do {
                val pollResult = suspendCancellableCoroutine<Byte> { continuation ->
                    val handle = uniffiContinuationHandleMap.insert(continuation)
                    continuation.invokeOnCancellation {
                        cancelFunc(rustFuture)
                    }
                    pollFunc(
                        rustFuture,
                        uniffiRustFutureContinuationCallbackCallback,
                        handle
                    )
                }
            } while (pollResult != UNIFFI_RUST_FUTURE_POLL_READY);

            liftFunc(
                uniffiRustCallWithError(errorHandler) { status -> completeFunc(rustFuture, status) }
            )
        }
    } finally {
        freeFunc(rustFuture)
    }
}

{%- if ci.has_async_callback_interface_definition() %}
internal inline fun<T> uniffiTraitInterfaceCallAsync(
    crossinline makeCall: suspend () -> T,
    crossinline handleSuccess: (T) -> Unit,
    crossinline handleError: (UniffiRustCallStatusByValue) -> Unit,
    uniffiOutDroppedCallback: UniffiForeignFutureDroppedCallbackStruct,
) {
    // Using `GlobalScope` is labeled as a "delicate API" and generally discouraged in Kotlin programs, since it breaks structured concurrency.
    // However, our parent task is a Rust future, so we're going to need to break structure concurrency in any case.
    //
    // Uniffi does its best to support structured concurrency across the FFI.
    // If the Rust future is dropped, `uniffiForeignFutureDroppedCallbackImpl` is called, which will cancel the Kotlin coroutine if it's still running.
    @OptIn(DelicateCoroutinesApi::class)
    val job = GlobalScope.launch coroutineBlock@ {
        // Note: it's important we call either `handleSuccess` or `handleError` exactly once.  Each
        // call consumes an Arc reference, which means there should be no possibility of a double
        // call.  The following code is structured so that it will never call both `handleSuccess`
        // and `handleError`, even in the face of weird exceptions.
        //
        // In extreme circumstances we may not call either, for example if we fail to make the FFI
        // call to `handleSuccess`.  This means we will leak the Arc reference, which is better than
        // double-freeing it.
        val callResult = try {
            makeCall()
        } catch(e: kotlin.Exception) {
            val status = UniffiRustCallStatusByValue(
                UNIFFI_CALL_UNEXPECTED_ERROR,
                {{ Type::String.borrow()|lower_fn }}(e.toString()),
            )
            handleError(status)
            return@coroutineBlock
        }
        handleSuccess(callResult)
    }
    val handle = uniffiForeignFutureHandleMap.insert(job)
    uniffiOutDroppedCallback.uniffiSetValue(UniffiForeignFutureDroppedCallbackStructUniffiByValue(handle, uniffiForeignFutureDroppedCallbackImpl))
}

internal inline fun<T, reified E: Throwable> uniffiTraitInterfaceCallAsyncWithError(
    crossinline makeCall: suspend () -> T,
    crossinline handleSuccess: (T) -> Unit,
    crossinline handleError: (UniffiRustCallStatusByValue) -> Unit,
    crossinline lowerError: (E) -> RustBufferByValue,
    uniffiOutDroppedCallback: UniffiForeignFutureDroppedCallbackStruct,
) {
    // See uniffiTraitInterfaceCallAsync for details on `DelicateCoroutinesApi`
    @OptIn(DelicateCoroutinesApi::class)
    val job = GlobalScope.launch coroutineBlock@ {
        // See the note in uniffiTraitInterfaceCallAsync for details on `handleSuccess` and
        // `handleError`.
        val callResult = try {
            makeCall()
        } catch(e: kotlin.Exception) {
            if (e is E) {
                val status = UniffiRustCallStatusByValue(
                    UNIFFI_CALL_ERROR,
                    lowerError(e),
                )
                handleError(status)
            } else {
                val status = UniffiRustCallStatusByValue(
                    UNIFFI_CALL_UNEXPECTED_ERROR,
                    {{ Type::String.borrow()|lower_fn }}(e.toString()),
                )
                handleError(status)
            }
            return@coroutineBlock
        }
        handleSuccess(callResult)
    }
    val handle = uniffiForeignFutureHandleMap.insert(job)
    uniffiOutDroppedCallback.uniffiSetValue(UniffiForeignFutureDroppedCallbackStructUniffiByValue(handle, uniffiForeignFutureDroppedCallbackImpl))
}

internal val uniffiForeignFutureHandleMap = UniffiHandleMap<Job>()

{%- endif %}
