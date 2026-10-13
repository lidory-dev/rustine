
{%- let obj = ci.get_object_definition(name).unwrap() %}
{%- let interface_name = self::object_interface_name(ci, obj) %}
{%- let impl_class_name = self::object_impl_name(ci, obj) %}
{%- let methods = obj.methods() %}
{%- let interface_docstring = obj.docstring() %}
{%- let is_error = ci.is_name_used_as_error(name) %}
{%- let ffi_converter_name = obj|ffi_converter_name %}
{%- let trait_impls = self::object_trait_impls(obj) %}
{%- let actual -%}
{%- if config.kotlin_multiplatform -%}
{%-     let actual = "actual" -%}
{%- else -%}
{%-     let actual = "" -%}
{%- endif %}
{%- let actual_override -%}
{%- if config.kotlin_multiplatform -%}
{%-     let actual_override = "actual override" -%}
{%- else -%}
{%-     let actual_override = "override" -%}
{%- endif %}

{%- let actual_prefix -%}
{%- if config.kotlin_multiplatform -%}
{%-     let actual_prefix = "actual " -%}
{%- else -%}
{%-     let actual_prefix = "" -%}
{%- endif %}

{%- macro emit_actual %}{% if config.kotlin_multiplatform %}actual {% endif %}{% endmacro -%}

{%- call kt::docstring(obj, 0) %}
{% if (is_error) %}
{{ visibility() }}{% call emit_actual %}open class {{ impl_class_name }} : kotlin.Exception, Disposable, {{ interface_name }}
{%- if trait_impls.compare_to().is_some() %}, Comparable<{{ impl_class_name }}>{% endif %} {
{% else -%}
{{ visibility() }}{% call emit_actual %}open class {{ impl_class_name }}: Disposable, {{ interface_name }}
{%- for t in obj.trait_impls() -%}
, {{ self::trait_interface_name(ci, t.trait_ty)? }}
{%- endfor %}
{%- if trait_impls.compare_to().is_some() %}, Comparable<{{ impl_class_name }}>{% endif %} {
{%- endif %}

    /**
     * @suppress
     */
    @Suppress("UNUSED_PARAMETER")
    {{ visibility() }}constructor(withHandle: UniffiWithHandle, handle: Long) {
        this.handle = handle
        this.cleanable = UniffiLib.CLEANER.register(this, UniffiHandleDestroyer(handle))
    }

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    @Suppress("UNUSED_PARAMETER")
    {{ visibility() }}{% call emit_actual %}constructor(noHandle: NoHandle) {
        this.handle = 0L
        this.cleanable = null
    }

    {%- match obj.primary_constructor() %}
    {%- when Some(cons) %}
    {%-     if cons.is_async() %}
    // Note no constructor generated for this object as it is async.
    {%-     else %}
    {%- call kt::docstring(cons, 4) %}
    {{ visibility() }}{% call emit_actual %}constructor({% call kt::arg_list(cons, false) -%}) : this(
        UniffiWithHandle,
        {% call kt::to_ffi_call(cons, 8) %},
    )
    {%-     endif %}
    {%- when None %}
    {%- endmatch %}

    protected val handle: Long
    protected val cleanable: UniffiCleaner.Cleanable?

    private val wasDestroyed: kotlinx.atomicfu.AtomicBoolean = kotlinx.atomicfu.atomic(false)
    private val callCounter: kotlinx.atomicfu.AtomicLong = kotlinx.atomicfu.atomic(1L)

    private val lock = kotlinx.atomicfu.locks.ReentrantLock()

    private fun <T> synchronized(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    {% call emit_actual %}override fun destroy() {
        // Only allow a single call to this method.
        // TODO: maybe we should log a warning if called more than once?
        if (this.wasDestroyed.compareAndSet(false, true)) {
            // This decrement always matches the initial count of 1 given at creation time.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable?.clean()
            }
        }
    }

    {% call emit_actual %}override fun close() {
        synchronized { this.destroy() }
    }

    internal inline fun <R> callWithHandle(block: (handle: Long) -> R): R {
        // Check and increment the call counter, to keep the object alive.
        // This needs a compare-and-set retry loop in case of concurrent updates.
        do {
            val c = this.callCounter.value
            if (c == 0L) {
                throw IllegalStateException("${this::class::simpleName} object has already been destroyed")
            }
            if (c == Long.MAX_VALUE) {
                throw IllegalStateException("${this::class::simpleName} call counter would overflow")
            }
        } while (! this.callCounter.compareAndSet(c, c + 1L))
        // Now we can safely do the method call without the handle being freed concurrently.
        try {
            return block(this.uniffiCloneHandle())
        } finally {
            // This decrement always matches the increment we performed above.
            if (this.callCounter.decrementAndGet() == 0L) {
                cleanable?.clean()
            }
        }
    }

    // Use a static inner class instead of a closure so as not to accidentally
    // capture `this` as part of the cleanable's action.
    private class UniffiHandleDestroyer(private val handle: Long) : Disposable {
        override fun destroy() {
            if (handle == 0L) {
                // Fake object created with `NoHandle`, don't try to free.
                return
            }
            uniffiRustCall { status ->
                UniffiLib.{{ obj.ffi_object_free().name() }}(handle, status)
            }
        }
    }

    /**
     * @suppress
     */
    {{ visibility() }}fun uniffiCloneHandle(): Long {
        if (handle == 0L) {
            throw InternalException("uniffiCloneHandle() called on NoHandle object")
        }
        return uniffiRustCall { status ->
            UniffiLib.{{ obj.ffi_object_clone().name() }}(handle, status)
        }
    }
    {%- if obj.has_callback_interface() %}

    // Whether a Rust object backs this instance. Fake objects created with `NoHandle` are lowered
    // like Kotlin implementations of the interface instead.
    internal val uniffiHasRustHandle: Boolean
        get() = handle != 0L
    {%- endif %}

    {% for meth in obj.methods() -%}
    {%- call kt::func_decl_with_body(actual_override, meth, 4) -%}
    {% endfor %}

    {%- call kt::object_trait_impl_bodies(trait_impls, impl_class_name, actual_prefix) %}

    {# XXX - "companion object" confusion? How to have alternate constructors *and* be an error? #}
    {% if !obj.alternate_constructors().is_empty() -%}
    {{ visibility() }}{% call emit_actual %}companion object {
        {% for cons in obj.alternate_constructors() -%}
        {%- call kt::func_decl_with_body(actual, cons, 8) %}
        {% endfor %}
    }
    {% else %}
    {{ visibility() }}{% call emit_actual %}companion object
    {% endif %}
}

{% if is_error %}
{{ visibility() }}object {{ impl_class_name }}ErrorHandler : UniffiRustCallStatusErrorHandler<{{ impl_class_name }}> {
    override fun lift(errorBuf: RustBufferByValue): {{ impl_class_name }} {
        // Due to some mismatches in the ffi converter mechanisms, errors are a RustBuffer.
        val bb = errorBuf.asByteBuffer()
        if (bb == null) {
            throw InternalException("?")
        }
        return {{ ffi_converter_name }}.read(bb)
    }
}
{% endif %}

{% macro converter_type(obj) -%}
{%- if obj.has_callback_interface() -%}
{{ interface_name }}
{%- else -%}
{{ impl_class_name }}
{%- endif -%}
{%- endmacro %}

{{ visibility() }}object {{ ffi_converter_name }}: FfiConverter<{%- call converter_type(obj) -%}, Long> {
    {%- if obj.has_callback_interface() %}
    internal val handleMap = UniffiHandleMap<{%- call converter_type(obj) -%}>()

    override fun lower(value: {% call converter_type(obj) %}): Long {
        // Upstream checks `value is {{ impl_class_name }}` only. Fake objects created with `NoHandle`
        // are lowered like Kotlin implementations instead, so they keep working.
        return if (value is {{ impl_class_name }} && value.uniffiHasRustHandle) {
            // Rust-implemented object. Clone the handle and return it.
            value.uniffiCloneHandle()
        } else {
            // Kotlin object, generate a new vtable handle and return that.
            handleMap.insert(value)
        }
    }

    override fun lift(value: Long): {% call converter_type(obj) %} {
        return if ((value and 1L) == 0L) {
            // Rust-generated handle, construct a new class that uses the handle to implement the
            // interface.
            {{ impl_class_name }}(UniffiWithHandle, value)
        } else {
            // Kotlin-generated handle, get the object from the handle map.
            handleMap.remove(value)
        }
    }
    {%- else %}

    override fun lower(value: {% call converter_type(obj) %}): Long {
        return value.uniffiCloneHandle()
    }

    override fun lift(value: Long): {% call converter_type(obj) %} {
        return {{ impl_class_name }}(UniffiWithHandle, value)
    }
    {%- endif %}

    override fun read(buf: ByteBuffer): {% call converter_type(obj) %} {
        return lift(buf.getLong())
    }

    override fun allocationSize(value: {% call converter_type(obj) %}): ULong = 8UL

    override fun write(value: {% call converter_type(obj) %}, buf: ByteBuffer) {
        buf.putLong(lower(value))
    }
}
