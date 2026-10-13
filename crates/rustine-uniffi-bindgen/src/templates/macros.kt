
{#
// Template to call into rust. Used in several places.
// Variable names in `arg_list` should match up with arg lists
// passed to rust via `arg_list_lowered`
#}

{%- macro check_rust_buffer_length(length) -%}
    require({{ length }} <= Int.MAX_VALUE) {
        val length = {{ length }}
        "cannot handle RustBuffer longer than Int.MAX_VALUE bytes: length is $length"
    }
{%- endmacro %}

{%- macro to_ffi_call(func, indent) -%}
                        {%- match func.self_type() -%}
                        {%- when Some(Type::Object { .. }) -%}
                        callWithHandle {
{{ " "|repeat(indent) }}    {% call to_raw_ffi_call(func, indent + 4) %}
{{ " "|repeat(indent) }}{{ '}' }}
                        {%- else -%}
                        {%- call to_raw_ffi_call(func, indent) -%}
                        {%- endmatch -%}
{%- endmacro %}

{#-
// `&[u8]` and `[ByRef] bytes` arguments are lowered to a `ForeignBytes` value that is only valid
// while `uniffiWithForeignBytes` runs, so the whole call happens inside it.
-#}
{%- macro to_raw_ffi_call(func, indent) -%}
                        {{ func|borrowed_bytes_scope_begin }}
                        {%- match func.throws_type() -%}
                        {%- when Some(e) -%}
                        uniffiRustCallWithError({{ e|type_name(ci) }}ErrorHandler)
                        {%- else -%}
                        uniffiRustCall
                        {%- endmatch %} { uniffiRustCallStatus ->
                                {%- call lowered_arg_vals(func, indent + 4) %}
{{ " "|repeat(indent) }}    UniffiLib.{{ func.ffi_func().name() }}(
                                {%- call receiver_lowered(func, indent + 8) -%}
                                {%- call arg_list_lowered(func, indent + 8) %}
{{ " "|repeat(indent) }}        uniffiRustCallStatus,
{{ " "|repeat(indent) }}    ){{ func.ffi_func()|ffi_direct_return_narrowing(module_name) }}
{{ " "|repeat(indent) }}{{ '}' }}{{ func|borrowed_bytes_scope_end }}
{%- endmacro -%}

{#-
// Upstream lowers the receiver of a method before its arguments, so the receiver leaked when
// lowering an argument threw, e.g., for a destroyed object: the cloned handle of an object, or
// the `RustBuffer` of a record, an enum, or an error. Methods lower their arguments into local
// values with this macro first, and their receiver with `receiver_lowered` in the call. The
// arguments lowered before the one that throws still leak, like in upstream.
-#}
{%- macro lowered_arg_vals(func, indent) -%}
                        {%- if func.self_type().is_some() -%}
                        {%- for arg in func.arguments() -%}
                        {%- if !arg.is_borrowed_bytes() %}
{{ " "|repeat(indent) }}val {{ arg|lowered_arg_var_name }} = {{ arg|lower_fn }}({{ arg.name()|var_name }})
                        {%- endif -%}
                        {%- endfor -%}
                        {%- endif -%}
{%- endmacro -%}

{#- The receiver of a method. Objects clone their handle inside `callWithHandle()`. -#}
{%- macro receiver_lowered(func, indent) -%}
                        {%- match func.self_type() %}
                        {%- when Some(Type::Object { .. }) %}
{{ " "|repeat(indent) }}uniffiCloneHandleUnchecked(),
                        {%- when Some(t) %}
{{ " "|repeat(indent) }}{{ t|lower_fn }}(this),
                        {%- when None %}
                        {%- endmatch -%}
{%- endmacro -%}

{%- macro func_decl(func_decl, callable, indent, is_decl_override) %}
                        {%- call docstring(callable, indent) -%}
                        {%- match callable.throws_type() -%}
                        {%-     when Some(throwable) %}
{{ " "|repeat(indent) }}@Throws({{ throwable|type_name(ci) }}::class {%- if callable.is_async() -%}, kotlin.coroutines.cancellation.CancellationException::class{%- endif -%})
                        {%-     else -%}
                        {%- endmatch %}
{{ " "|repeat(indent) }}{{ visibility() }}{% if func_decl.len() != 0 -%}{{ func_decl }} {% endif -%}
                        {%- if callable.is_async() -%}suspend {% endif -%}
                        fun {{ callable.name()|fn_name }}(
                            {%- call arg_list(callable, is_decl_override || callable.self_type().is_none()) -%}
                        )
                        {%- match callable.return_type() -%}
                        {%-     when Some(return_type) %}: {{ return_type|type_name(ci) -}}
                        {%-     else -%}
                        {%- endmatch -%}
{% endmacro %}

{%- macro func_decl_with_body(func_decl, callable, indent) %}
                        {%- call docstring(callable, indent) -%}
                        {%- match callable.throws_type() -%}
                        {%-     when Some(throwable) %}
{{ " "|repeat(indent) }}@Throws({{ throwable|type_name(ci) }}::class {%- if callable.is_async() -%}, kotlin.coroutines.cancellation.CancellationException::class{%- endif -%})
                        {%-     else -%}
                        {%- endmatch %}
{{ " "|repeat(indent) }}{{ visibility() }}{% if func_decl.len() != 0 -%}{{ func_decl }} {% endif -%}
                        {%- if callable.is_async() -%}suspend {% endif -%}
                        fun {{ callable.name()|fn_name }}(
                            {%- call arg_list(callable, false) -%}
                        )
                        {%- match callable.return_type() -%}
                        {%-     when Some(return_type) %}: {{ return_type|type_name(ci) -}}
                        {%-     else -%}
                        {%- endmatch %} {% call func_body(callable, indent) %}
{% endmacro %}

{#-
// The body of a function that calls `callable` into Rust, starting with the opening brace.
-#}
{%- macro func_body(callable, indent) -%}
{
                            {%- if callable.is_async() %}
{{ " "|repeat(indent) }}    return {% call call_async(callable, indent + 4) -%}
                            {%- else -%}
                            {%- match callable.return_type() -%}
                            {%-     when Some(return_type) %}
{{ " "|repeat(indent) }}    return {{ return_type|lift_fn }}({%- call to_ffi_call(callable, indent + 4) -%})
                            {%-     else %}
{{ " "|repeat(indent) }}    {% call to_ffi_call(callable, indent + 4) -%}
                            {%- endmatch %}
                            {%- endif %}
{{ " "|repeat(indent) }}{{ '}' }}
{%- endmacro %}

{#-
// Methods of records, enums and errors.
//
// These types are plain classes in common code, but the FFI calls exist only in the platform
// code. Their members call an internal extension function for each method instead, which common
// code declares as `expect` and the platform code defines (see the `value_method_helper` filter).
-#}

{#-
// The public member of the class, which calls the helper. A sealed class can have variants that
// shadow the types of the signature (mozilla/uniffi-rs#2698), so they are fully qualified.
-#}
{%- macro value_method_decl(callable, indent) %}
                        {%- call docstring(callable, indent) -%}
                        {%- match callable.throws_type() -%}
                        {%-     when Some(throwable) %}
{{ " "|repeat(indent) }}@Throws({{ throwable|qualified_type_name(ci, config) }}::class {%- if callable.is_async() -%}, kotlin.coroutines.cancellation.CancellationException::class{%- endif -%})
                        {%-     else -%}
                        {%- endmatch %}
{{ " "|repeat(indent) }}{{ visibility() }}{% if callable.is_async() -%}suspend {% endif -%}
                        fun {{ callable.name()|fn_name }}(
                            {%- call qualified_arg_list(callable) -%}
                        )
                        {%- match callable.return_type() -%}
                        {%-     when Some(return_type) %}: {{ return_type|qualified_type_name(ci, config) }} {
{{ " "|repeat(indent) }}    return this.{{ callable|value_method_helper }}({% call arg_names(callable) %})
                        {%-     else %} {
{{ " "|repeat(indent) }}    this.{{ callable|value_method_helper }}({% call arg_names(callable) %})
                        {%- endmatch %}
{{ " "|repeat(indent) }}{{ '}' }}
{%- endmacro %}

{#- The signature of the helper of `callable`, a method of `receiver`. -#}
{%- macro value_method_helper_signature(func_decl, callable, receiver) -%}
internal {% if func_decl.len() != 0 -%}{{ func_decl }} {% endif -%}
{%- if callable.is_async() -%}suspend {% endif -%}
fun {{ receiver }}.{{ callable|value_method_helper }}(
    {%- call arg_list(callable, false) -%}
)
{%- match callable.return_type() -%}
{%-     when Some(return_type) %}: {{ return_type|type_name(ci) -}}
{%-     else -%}
{%- endmatch -%}
{%- endmacro %}

{#- The `expect` declarations of the helpers in common code. -#}
{%- macro value_method_helper_expects(methods, trait_impls, receiver) %}
{%- if config.kotlin_multiplatform && (!methods.is_empty() || !trait_impls.methods().is_empty()) %}

{% for meth in methods -%}
{% call value_method_helper_signature("expect", meth, receiver) %}
{% endfor -%}
{% for meth in trait_impls.methods() -%}
{% call value_method_helper_signature("expect", meth, receiver) %}
{% endfor -%}
{%- endif %}
{%- endmacro %}

{#- The definitions of the helpers in the platform code. -#}
{%- macro value_method_helper_actuals(methods, trait_impls, receiver) %}
{%- let helper_decl -%}
{%- if config.kotlin_multiplatform -%}
{%-     let helper_decl = "actual" -%}
{%- else -%}
{%-     let helper_decl = "" -%}
{%- endif -%}
{%- for meth in methods %}

{% call value_method_helper_signature(helper_decl, meth, receiver) %} {% call func_body(meth, 0) %}
{%- endfor %}
{%- for meth in trait_impls.methods() %}

{% call value_method_helper_signature(helper_decl, meth, receiver) %} {% call func_body(meth, 0) %}
{%- endfor %}
{%- endmacro %}

{#- The definitions of the helpers in the stub code. -#}
{%- macro value_method_helper_stubs(methods, trait_impls, receiver) %}
{%- for meth in methods %}

{% call value_method_helper_signature("actual", meth, receiver) %} {
    TODO()
}
{%- endfor %}
{%- for meth in trait_impls.methods() %}

{% call value_method_helper_signature("actual", meth, receiver) %} {
    TODO()
}
{%- endfor %}
{%- endmacro %}

{#-
// The members of a record, enum or error that implement its uniffi traits with the helpers.
// `modifier` is `final override` on a sealed class, which keeps its data class and data object
// subclasses from generating their own. The Kotlin types are fully qualified, as the variants of a
// sealed class can shadow them, e.g., `enum Value { String(String) }`.
-#}
{%- macro value_trait_impls(trait_impls, type_name, modifier, indent) %}
{%- if let Some(fmt) = trait_impls.to_string() %}

{{ " "|repeat(indent) }}// The local Rust `Display`/`Debug` implementation.
{{ " "|repeat(indent) }}{{ modifier }} fun toString(): kotlin.String {
{{ " "|repeat(indent) }}    return this.{{ fmt|value_method_helper }}()
{{ " "|repeat(indent) }}{{ '}' }}
{%- endif %}
{%- if let Some(eq) = trait_impls.equals() %}

{{ " "|repeat(indent) }}// The local Rust `Eq` implementation - only `eq` is used.
{{ " "|repeat(indent) }}{{ modifier }} fun equals(other: kotlin.Any?): kotlin.Boolean {
{{ " "|repeat(indent) }}    if (other !is {{ type_name }}) return false
{{ " "|repeat(indent) }}    return this.{{ eq|value_method_helper }}(other)
{{ " "|repeat(indent) }}{{ '}' }}
{%- endif %}
{%- if let Some(hash) = trait_impls.hash_code() %}

{{ " "|repeat(indent) }}// The local Rust `Hash` implementation
{{ " "|repeat(indent) }}{{ modifier }} fun hashCode(): kotlin.Int {
{{ " "|repeat(indent) }}    return this.{{ hash|value_method_helper }}().toInt()
{{ " "|repeat(indent) }}{{ '}' }}
{%- endif %}
{%- if let Some(cmp) = trait_impls.compare_to() %}

{{ " "|repeat(indent) }}// The local Rust `Ord` implementation
{{ " "|repeat(indent) }}{{ modifier }} fun compareTo(other: {{ type_name }}): kotlin.Int {
{{ " "|repeat(indent) }}    return this.{{ cmp|value_method_helper }}(other).toInt()
{{ " "|repeat(indent) }}{{ '}' }}
{%- endif %}
{%- endmacro %}

{#-
// The uniffi traits of objects: the declarations in the `expect` class, ...
-#}
{%- macro object_trait_impl_decls(trait_impls, impl_class_name) %}
{%- if trait_impls.to_string().is_some() %}
    override fun toString(): kotlin.String
{%- endif %}
{%- if trait_impls.equals().is_some() %}
    override fun equals(other: kotlin.Any?): kotlin.Boolean
{%- endif %}
{%- if trait_impls.hash_code().is_some() %}
    override fun hashCode(): kotlin.Int
{%- endif %}
{%- if trait_impls.compare_to().is_some() %}
    override fun compareTo(other: {{ impl_class_name }}): kotlin.Int
{%- endif %}
{%- endmacro %}

{#-
// ... the definitions, which call into Rust, ...
//
// Upstream uses the object name in `equals()` and `compareTo()`, which is the interface for trait
// interfaces that Kotlin can implement. Use the class of the Rust implementation, like the
// `Comparable` supertype.
-#}
{%- macro object_trait_impl_bodies(trait_impls, impl_class_name, decl_prefix) %}
{%- if let Some(fmt) = trait_impls.to_string() %}

    // The local Rust `Display`/`Debug` implementation.
    {{ decl_prefix }}override fun toString(): kotlin.String {
        return {{ fmt.return_type().unwrap()|lift_fn }}({% call to_ffi_call(fmt, 8) %})
    }
{%- endif %}
{%- if let Some(eq) = trait_impls.equals() %}

    // The local Rust `Eq` implementation - only `eq` is used.
    {{ decl_prefix }}override fun equals(other: kotlin.Any?): kotlin.Boolean {
        if (this === other) return true
        if (other !is {{ impl_class_name }}) return false
        return {{ eq.return_type().unwrap()|lift_fn }}({% call to_ffi_call(eq, 8) %})
    }
{%- endif %}
{%- if let Some(hash) = trait_impls.hash_code() %}

    // The local Rust `Hash` implementation
    {{ decl_prefix }}override fun hashCode(): kotlin.Int {
        return {{ hash.return_type().unwrap()|lift_fn }}({%- call to_ffi_call(hash, 8) %}).toInt()
    }
{%- endif %}
{%- if let Some(cmp) = trait_impls.compare_to() %}

    // The local Rust `Ord` implementation
    {{ decl_prefix }}override fun compareTo(other: {{ impl_class_name }}): kotlin.Int {
        return {{ cmp.return_type().unwrap()|lift_fn }}({%- call to_ffi_call(cmp, 8) %}).toInt()
    }
{%- endif %}
{%- endmacro %}

{#- ... and the stubs. -#}
{%- macro object_trait_impl_stubs(trait_impls, impl_class_name) %}
{%- if trait_impls.to_string().is_some() %}

    actual override fun toString(): kotlin.String {
        TODO()
    }
{%- endif %}
{%- if trait_impls.equals().is_some() %}

    actual override fun equals(other: kotlin.Any?): kotlin.Boolean {
        TODO()
    }
{%- endif %}
{%- if trait_impls.hash_code().is_some() %}

    actual override fun hashCode(): kotlin.Int {
        TODO()
    }
{%- endif %}
{%- if trait_impls.compare_to().is_some() %}

    actual override fun compareTo(other: {{ impl_class_name }}): kotlin.Int {
        TODO()
    }
{%- endif %}
{%- endmacro %}

{%- macro func_decl_with_stub(func_decl, callable, indent) %}
                        {%- call docstring(callable, indent) %}
{{ " "|repeat(indent) }}{{ visibility() }}{% if func_decl.len() != 0 -%}{{ func_decl }} {% endif -%}
                        {%- if callable.is_async() -%}suspend {% endif -%}
                        fun {{ callable.name()|fn_name }}(
                            {%- call arg_list(callable, false) -%}
                        )
                        {%- match callable.return_type() -%}
                        {%-     when Some(return_type) %}: {{ return_type|type_name(ci) -}}
                        {%-     else -%}
                        {%- endmatch %} {
{{ " "|repeat(indent) }}    TODO()
{{ " "|repeat(indent) }}{{ '}' }}
{% endmacro %}

{%- macro call_async(callable, indent) -%}
                        uniffiRustCallAsync(
                            {%- match callable.self_type() %}
                            {%- when Some(Type::Object { .. }) %}
{{ " "|repeat(indent) }}    callWithHandle {
                                {%- call async_method_call(callable, indent + 8) %}
{{ " "|repeat(indent) }}    },
                            {%- when Some(_) %}
{{ " "|repeat(indent) }}    run {
                                {%- call async_method_call(callable, indent + 8) %}
{{ " "|repeat(indent) }}    },
                            {%- when None %}
{{ " "|repeat(indent) }}    UniffiLib.{{ callable.ffi_func().name() }}(
                                {%- call arg_list_lowered(callable, indent + 8) %}
{{ " "|repeat(indent) }}    ),
                            {%- endmatch %}
{{ " "|repeat(indent) }}    {{ callable|async_poll(ci) }},
{{ " "|repeat(indent) }}    {{ callable|async_complete(ci, module_name) }},
{{ " "|repeat(indent) }}    {{ callable|async_free(ci) }},
{{ " "|repeat(indent) }}    {{ callable|async_cancel(ci) }},
{{ " "|repeat(indent) }}    // lift function
                            {%- match callable.return_type() -%}
                            {%- when Some(return_type) %}
{{ " "|repeat(indent) }}    { {{ return_type|lift_fn }}(it) },
                            {%- when None %}
{{ " "|repeat(indent) }}    { },
{{ " "|repeat(indent) }}    {% endmatch %}
{{ " "|repeat(indent) }}    // Error FFI converter
                            {%- match callable.throws_type() -%}
                            {%- when Some(e) %}
{{ " "|repeat(indent) }}    {{ e|type_name(ci) }}ErrorHandler,
                            {%- when None %}
{{ " "|repeat(indent) }}    UniffiNullRustCallStatusErrorHandler,
                            {%- endmatch %}
{{ " "|repeat(indent) }})
{%- endmacro %}

{#-
// The call that creates the Rust future of an async method, in a block that holds the lowered
// arguments, see `lowered_arg_vals`.
-#}
{%- macro async_method_call(callable, indent) -%}
                        {%- call lowered_arg_vals(callable, indent) %}
{{ " "|repeat(indent) }}UniffiLib.{{ callable.ffi_func().name() }}(
                        {%- call receiver_lowered(callable, indent + 4) -%}
                        {%- call arg_list_lowered(callable, indent + 4) %}
{{ " "|repeat(indent) }})
{%- endmacro -%}

{%- macro arg_list_lowered(func, indent) -%}
                        {%- for arg in func.arguments() %}
                        {%- if arg.is_borrowed_bytes() %}
{{ " "|repeat(indent) }}{{ arg|borrowed_bytes_var_name }},
                        {%- elif func.self_type().is_some() %}
{{ " "|repeat(indent) }}{{ arg|lowered_arg_var_name }},
                        {%- else %}
{{ " "|repeat(indent) }}{{ arg|lower_fn }}({{ arg.name()|var_name }}),
                        {%- endif %}
                        {%- endfor -%}
{%- endmacro -%}

{#-
// Arglist as used in kotlin declarations of methods, functions and constructors.
// If is_decl, then default values be specified.
// Note the var_name and type_name filters.
-#}

{% macro arg_list(func, is_decl) %}
{%- for arg in func.arguments() -%}
        {{ arg.name()|var_name }}: {{ arg|type_name(ci) }}
{%-     if is_decl %}
{%-         match arg.default_value() %}
{%-             when Some with(default) %} = {{ default|render_default(arg, ci, config) }}
{%-             else %}
{%-         endmatch %}
{%-     endif %}
{%-     if !loop.last %}, {% endif -%}
{%- endfor %}
{%- endmacro %}

{#-
// Like `arg_list(func, true)`, with fully qualified types, and default values that name their types
// through aliases, see `aliased_default()`.
-#}
{% macro qualified_arg_list(func) %}
{%- for arg in func.arguments() -%}
        {{ arg.name()|var_name }}: {{ arg|qualified_type_name(ci, config) }}
{%-         match arg.default_value() %}
{%-             when Some with(default) %} = {{ self.aliased_default(default, arg)? }}
{%-             else %}
{%-         endmatch %}
{%-     if !loop.last %}, {% endif -%}
{%- endfor %}
{%- endmacro %}

{#- The argument names of `func`, to pass its arguments on to another function. -#}
{%- macro arg_names(func) %}
{%- for arg in func.arguments() -%}
{{ arg.name()|var_name }}{% if !loop.last %}, {% endif -%}
{%- endfor %}
{%- endmacro %}

{#-
// Arglist as used in the UniffiLib function declarations.
// Note unfiltered name but ffi_type_name filters.
-#}
{%- macro arg_list_ffi_decl(func, indent) -%}
                        {%- for arg in func.arguments() %}
{{ " "|repeat(indent) }}{{ arg.name()|var_name }}: {{ arg.type_().borrow()|ffi_type_name_by_value(ci) }},
                        {%- endfor -%}
                        {%- if func.has_rust_call_status_arg() %}
{{ " "|repeat(indent) }}uniffiCallStatus: UniffiRustCallStatus,
                        {%- endif -%}
{%- endmacro -%}

{%- macro arg_list_ffi_call_native(func) %}
    {%- for arg in func.arguments() -%}
        {%- if let Some(callback) = arg.type_().borrow()|ffi_as_callback(ci) -%}
        {%- if callback|ffi_callback_needs_casting_native %}
        {{ arg.name()|var_name }} as {{ci.namespace()}}.cinterop.{{ arg.type_().borrow()|ffi_type_name_for_ffi_struct(ci) }},
        {%- else %}
        {{ arg.name()|var_name }},
        {%- endif -%}
        {%- else %}
        {{ arg.name()|var_name }}{{- arg.type_().borrow()|ffi_cast_to_local_rust_buffer_if_needed(ci) -}},
        {%- endif -%}
    {%- endfor -%}
    {%- if func.has_rust_call_status_arg() %}
        uniffiCallStatus,
    {%- endif -%}
{%- endmacro -%}

{% macro field_name(field, field_num) %}
{%- if field.name().is_empty() -%}
v{{- field_num -}}
{%- else -%}
{{ field.name()|var_name }}
{%- endif -%}
{%- endmacro %}

{% macro field_name_unquoted(field, field_num) %}
{%- if field.name().is_empty() -%}
v{{- field_num -}}
{%- else -%}
{{ field.name()|var_name|unquote }}
{%- endif -%}
{%- endmacro %}

{#- Macro for destroying fields -#}
{%- macro destroy_fields(member, indent) %}
{{ " "|repeat(indent) }}Disposable.destroy(
                            {%- for field in member.fields() %}
{{ " "|repeat(indent) }}    this.{%- call field_name(field, loop.index) -%},
                            {%- endfor %}
{{ " "|repeat(indent) }})
{%- endmacro -%}

{#-
// Macro for generating equals() and hashCode() of data classes with ByteArray fields, which
// Kotlin compares by reference. Either can be left out when a uniffi trait implements it. The
// Kotlin types are fully qualified, as the variants of a sealed class can shadow them.
-#}
{%- macro generate_equals_hash_code(data_class, type_name, indent, gen_equals, gen_hash_code) %}
{%- if gen_equals %}
{{ " "|repeat(indent) }}override fun equals(other: kotlin.Any?): kotlin.Boolean {
{{ " "|repeat(indent) }}    if (this === other) return true
{{ " "|repeat(indent) }}    if (other == null || this::class != other::class) return false

{{ " "|repeat(indent) }}    other as {{ type_name }}

                            {%- if data_class.fields().len() == 1 -%}
                            {%-     for field in data_class.fields() %}
                            {%-         match field|as_data_class_field_type -%}
                            {%-             when DataClassFieldType::Bytes %}
{{ " "|repeat(indent) }}    return {% call field_name(field, loop.index) %}.contentEquals(other.{% call field_name(field, loop.index) %})
                            {%-             when DataClassFieldType::NullableBytes %}
{{ " "|repeat(indent) }}    if ({% call field_name(field, loop.index) %} != null) {
{{ " "|repeat(indent) }}        if (other.{% call field_name(field, loop.index) %} == null) return false
{{ " "|repeat(indent) }}        if (!{% call field_name(field, loop.index) %}.contentEquals(other.{% call field_name(field, loop.index) %})) return false
{{ " "|repeat(indent) }}    }

{{ " "|repeat(indent) }}    return true
                            {%-             else %}
{{ " "|repeat(indent) }}    return {% call field_name(field, loop.index) %} == other.{% call field_name(field, loop.index) %}
                            {%-         endmatch -%}
                            {%-     endfor -%}
                            {%- else -%}
                            {%-     for field in data_class.fields() -%}
                            {%-         match field|as_data_class_field_type -%}
                            {%-             when DataClassFieldType::Bytes %}
{{ " "|repeat(indent) }}    if (!{% call field_name(field, loop.index) %}.contentEquals(other.{% call field_name(field, loop.index) %})) return false
                            {%-             when DataClassFieldType::NullableBytes %}
{{ " "|repeat(indent) }}    if ({% call field_name(field, loop.index) %} != null) {
{{ " "|repeat(indent) }}        if (other.{% call field_name(field, loop.index) %} == null) return false
{{ " "|repeat(indent) }}        if (!{% call field_name(field, loop.index) %}.contentEquals(other.{% call field_name(field, loop.index) %})) return false
{{ " "|repeat(indent) }}    }
                            {%-             else %}
{{ " "|repeat(indent) }}    if ({% call field_name(field, loop.index) %} != other.{% call field_name(field, loop.index) %}) return false
                            {%-         endmatch -%}
                            {%-     endfor %}

{{ " "|repeat(indent) }}    return true
                            {%- endif %}
{{ " "|repeat(indent) }}{{ '}' }}
{%- endif %}
{%- if gen_hash_code %}
{{ " "|repeat(indent) }}override fun hashCode(): kotlin.Int {
                            {%- for field in data_class.fields() -%}
                            {%-     if loop.first -%}
                            {%-         if data_class.fields().len() == 1 %}
{{ " "|repeat(indent) }}    return{{ ' ' }}
                            {%-         else %}
{{ " "|repeat(indent) }}    var result ={{ ' ' }}
                            {%-         endif -%}
                            {%-     else %}
{{ " "|repeat(indent) }}    result = 31 * result +{{ ' ' }}
                            {%-     endif -%}
                            {%-     match field|as_data_class_field_type -%}
                            {%-         when DataClassFieldType::Bytes -%}
                            {% call field_name(field, loop.index) %}.contentHashCode()
                            {%-         when DataClassFieldType::NullableBytes -%}
                            ({% call field_name(field, loop.index) %}?.contentHashCode() ?: 0)
                            {%-         when DataClassFieldType::NonNullableNonBytes -%}
                            {% call field_name(field, loop.index) %}.hashCode()
                            {%-         when DataClassFieldType::NullableNonBytes -%}
                            ({% call field_name(field, loop.index) %}?.hashCode() ?: 0)
                            {%-     endmatch -%}
                            {%- endfor -%}
                            {%- if data_class.fields().len() > 1 %}
{{ " "|repeat(indent) }}    return result
                            {%- endif %}
{{ " "|repeat(indent) }}{{ '}' }}
{%- endif %}
{%- endmacro -%}

{%- macro docstring_value(maybe_docstring, indent_spaces) %}
{%- match maybe_docstring %}
{%- when Some(docstring) %}
{{ docstring|docstring(indent_spaces) }}
{%- else %}
{%- endmatch %}
{%- endmacro %}

{%- macro docstring(defn, indent_spaces) %}
{%- call docstring_value(defn.docstring(), indent_spaces) %}
{%- endmacro %}
