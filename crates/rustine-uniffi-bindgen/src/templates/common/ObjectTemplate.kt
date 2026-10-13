
{%- let obj = ci.get_object_definition(name).unwrap() %}
{%- let interface_name = self::object_interface_name(ci, obj) %}
{%- let impl_class_name = self::object_impl_name(ci, obj) %}
{%- let methods = obj.methods() %}
{%- let interface_docstring = obj.docstring() %}
{%- let is_error = ci.is_name_used_as_error(name) %}
{%- let ffi_converter_name = obj|ffi_converter_name %}
{%- let trait_impls = self::object_trait_impls(obj) %}

{%- include "Interface.kt" %}
{% if config.kotlin_multiplatform %}
{% call kt::docstring(obj, 0) %}
{% if (is_error) %}
{{ visibility() }}expect open class {{ impl_class_name }} : kotlin.Exception, Disposable, {{ interface_name }}
{%- if trait_impls.compare_to().is_some() %}, Comparable<{{ impl_class_name }}>{% endif %} {
{% else -%}
{{ visibility() }}expect open class {{ impl_class_name }}: Disposable, {{ interface_name }}
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
    {%- match obj.primary_constructor() %}
    {%- when Some(cons) %}
    {%-     if cons.is_async() %}
    @Suppress("ConvertSecondaryConstructorToPrimary")
    {%-     else %}
    {%- call kt::docstring(cons, 4) %}
    {%-     endif %}
    {%- when None %}
    @Suppress("ConvertSecondaryConstructorToPrimary")
    {%- endmatch %}
    {{ visibility() }}constructor(noHandle: NoHandle)

    {% match obj.primary_constructor() -%}
    {%- when Some(cons) -%}
    {%-     if cons.is_async() -%}
    // Note no constructor generated for this object as it is async.
    {%     else -%}
    {%- call kt::docstring(cons, 4) %}
    {{ visibility() }}constructor({% call kt::arg_list(cons, true) -%})
    {%-     endif %}
    {%- when None %}
    {%- endmatch %}

    /**
     * Whether the current object has been destroyed and its reference is gone in the Rust side.
     */
    {{ visibility() }}val uniffiIsDestroyed: Boolean

    override fun destroy()
    override fun close()

    {% for meth in obj.methods() -%}
    {%- call kt::func_decl("override", meth, 4, false) %}
    {% endfor %}

    {%- call kt::object_trait_impl_decls(trait_impls, impl_class_name) %}

    {# XXX - "companion object" confusion? How to have alternate constructors *and* be an error? #}
    {%- if !obj.alternate_constructors().is_empty() -%}
    {{ visibility() }}companion object {
        {% for cons in obj.alternate_constructors() -%}
        {%- call kt::func_decl("", cons, 8, false) %}
        {% endfor %}
    }
    {% else %}
    {{ visibility() }}companion object
    {%- endif %}
}
{% endif %}