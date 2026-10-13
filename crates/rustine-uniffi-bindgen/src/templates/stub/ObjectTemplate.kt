
{%- let obj = ci.get_object_definition(name).unwrap() %}
{%- let interface_name = self::object_interface_name(ci, obj) %}
{%- let impl_class_name = self::object_impl_name(ci, obj) %}
{%- let methods = obj.methods() %}
{%- let interface_docstring = obj.docstring() %}
{%- let is_error = ci.is_name_used_as_error(name) %}
{%- let ffi_converter_name = obj|ffi_converter_name %}
{%- let trait_impls = self::object_trait_impls(obj) %}

{%- call kt::docstring(obj, 0) %}
{% if (is_error) %}
{{ visibility() }}actual open class {{ impl_class_name }} : kotlin.Exception, Disposable, {{ interface_name }}
{%- if trait_impls.compare_to().is_some() %}, Comparable<{{ impl_class_name }}>{% endif %} {
{% else -%}
{{ visibility() }}actual open class {{ impl_class_name }}: Disposable, {{ interface_name }}
{%- for t in obj.trait_impls() -%}
, {{ self::trait_interface_name(ci, config, t.trait_ty)? }}
{%- endfor %}
{%- if trait_impls.compare_to().is_some() %}, Comparable<{{ impl_class_name }}>{% endif %} {
{%- endif %}

    /**
     * This constructor can be used to instantiate a fake object. Only used for tests. Any
     * attempt to actually use an object constructed this way will fail as there is no
     * connected Rust object.
     */
    {{ visibility() }}actual constructor(noHandle: NoHandle)

    {%- match obj.primary_constructor() %}
    {%- when Some(cons) %}
    {%- call kt::docstring(cons, 4) %}

    {{ visibility() }}actual constructor({% call kt::arg_list(cons, false) -%}) {
        TODO()
    }
    {%- when None %}
    {%- endmatch %}

    {{ visibility() }}actual val uniffiIsDestroyed: Boolean
        get() = TODO()

    actual override fun destroy() {
        TODO()
    }

    actual override fun close() {
        TODO()
    }

    {% for meth in obj.methods() -%}
    {%- call kt::func_decl_with_stub("actual override", meth, 4) -%}
    {% endfor %}

    {%- call kt::object_trait_impl_stubs(trait_impls, impl_class_name) %}

    {# XXX - "companion object" confusion? How to have alternate constructors *and* be an error? #}
    {% if !obj.alternate_constructors().is_empty() -%}
    {{ visibility() }}actual companion object {
        {% for cons in obj.alternate_constructors() -%}
        {%- call kt::func_decl_with_stub("actual", cons, 8) %}
        {% endfor %}
    }
    {% else %}
    {{ visibility() }}actual companion object
    {% endif %}
}
