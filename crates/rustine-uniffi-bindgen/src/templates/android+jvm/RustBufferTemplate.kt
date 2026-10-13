{% include "ffi/RustBufferTemplate.kt" %}

@Structure.FieldOrder("capacity", "len", "data")
{{ visibility() }}open class RustBufferStruct(
    // Note: `capacity` and `len` are actually `ULong` values, but JVM only supports signed values.
    // When dealing with these fields, make sure to call `toULong()`.
    @JvmField {{ visibility() }}var capacity: Long,
    @JvmField {{ visibility() }}var len: Long,
    @JvmField {{ visibility() }}var data: Pointer?,
) : Structure() {
    {{ visibility() }}constructor(): this(0.toLong(), 0.toLong(), null)

    {{ visibility() }}class ByValue(
        capacity: Long,
        len: Long,
        data: Pointer?,
    ): RustBuffer(capacity, len, data), Structure.ByValue {
        {{ visibility() }}constructor(): this(0.toLong(), 0.toLong(), null)
    }

    /**
     * The equivalent of the `*mut RustBuffer` type.
     * Required for callbacks taking in an out pointer.
     *
     * Size is the sum of all values in the struct.
     */
    {{ visibility() }}class ByReference(
        capacity: Long,
        len: Long,
        data: Pointer?,
    ): RustBuffer(capacity, len, data), Structure.ByReference {
        {{ visibility() }}constructor(): this(0.toLong(), 0.toLong(), null)
    }
}

{{ visibility() }}typealias RustBuffer = RustBufferStruct
{{ visibility() }}typealias RustBufferByValue = RustBufferStruct.ByValue

internal fun RustBuffer.asByteBuffer(): ByteBuffer? {
    {% call kt::check_rust_buffer_length("this.len") %}
    return ByteBuffer(data?.getByteBuffer(0L, this.len) ?: return null)
}

internal fun RustBufferByValue.asByteBuffer(): ByteBuffer? {
    {% call kt::check_rust_buffer_length("this.len") %}
    return ByteBuffer(data?.getByteBuffer(0L, this.len) ?: return null)
}

// This is a helper for safely passing byte references into the rust code.
// `&[u8]` and `[ByRef] bytes` arguments are passed as `ForeignBytes`, see `uniffiWithForeignBytes`.

@Structure.FieldOrder("len", "data")
internal open class ForeignBytesStruct : Structure() {
    @JvmField var len: Int = 0
    @JvmField var data: Pointer? = null

    internal class ByValue : ForeignBytes(), Structure.ByValue
}
internal typealias ForeignBytes = ForeignBytesStruct
internal typealias ForeignBytesByValue = ForeignBytesStruct.ByValue

// Lower a `ByteArray` passed to a `&[u8]` or `[ByRef] bytes` argument, and make the call in
// `block`. The `ForeignBytes` value borrows memory that is only valid while `block` runs.
//
// Upstream takes a direct `java.nio.ByteBuffer` and passes a pointer to its memory without
// copying. The Kotlin Multiplatform API takes a `ByteArray` on every platform instead. JNA can't
// pass a pointer to a JVM array inside a struct passed by value, so the bytes are copied to native
// memory, which is freed when `block` returns.
internal inline fun <R> ByteArray.uniffiWithForeignBytes(block: (ForeignBytesByValue) -> R): R {
    val foreignBytes = ForeignBytesByValue()
    if (isEmpty()) {
        // Rust treats a null pointer with a length of 0 as an empty slice.
        return block(foreignBytes)
    }
    val memory = com.sun.jna.Memory(size.toLong())
    try {
        memory.write(0, this, 0, size)
        foreignBytes.len = size
        foreignBytes.data = memory
        return block(foreignBytes)
    } finally {
        memory.close()
    }
}
