
{%- let type_name = type_|type_name(ci) %}
{%- let ffi_converter_name = type_|ffi_converter_name %}
{%- let canonical_type_name = type_|canonical_name %}
{%- let trait_impls = self::enum_trait_impls(ci, e) %}

{% if e.is_flat() %}
{%- call kt::docstring(e, 0) %}
{{ visibility() }}sealed class {{ type_name }}(message: kotlin.String): kotlin.Exception(message){% if contains_object_references %}, Disposable {% endif %} {
    {% for variant in e.variants() -%}
    {%- call kt::docstring(variant, 4) %}
    {{ visibility() }}class {{ variant|error_variant_name }}(message: kotlin.String) : {{ type_name }}(message)
    {% endfor %}
}
{%- else %}
{%- call kt::docstring(e, 0) %}
{{ visibility() }}sealed class {{ type_name }}: kotlin.Exception(){% if contains_object_references %}, Disposable {% endif %}{% if trait_impls.compare_to().is_some() %}, Comparable<{{ type_name }}>{% endif %} {
    {% for variant in e.variants() -%}
    {%- call kt::docstring(variant, 4) %}
    {%- let variant_name = variant|error_variant_name %}
    {{ visibility() }}class {{ variant_name }}(
        {%- for field in variant.fields() -%}
        {%- call kt::docstring(field, 8) %}
        {{ visibility() }}val {% call kt::field_name(field, loop.index) %}: {{ field|qualified_type_name(ci, config) }},
        {%- endfor %}
    ) : {{ type_name }}() {
        override val message: kotlin.String
            get() = "{%- for field in variant.fields() %}{% call kt::field_name_unquoted(field, loop.index) %}=${ {% call kt::field_name(field, loop.index) %} }{% if !loop.last %}, {% endif %}{% endfor %}"
        {%- if contains_object_references %}

        override fun destroy() {
            {% if variant.has_fields() -%}
            {%- call kt::destroy_fields(variant, 12) -%}
            {%- else -%}
            // Nothing to destroy
            {%- endif %}
        }
        {%- endif %}
    }
    {% endfor %}
    {%- for meth in e.methods() %}
{% call kt::value_method_decl(meth, 4) %}
    {%- endfor %}
    {%- call kt::value_trait_impls(trait_impls, type_name, "override", 4) %}
}
{%- endif %}
{#- `check_value_type_members()` rejects methods on flat errors, and they implement no traits. #}
{%- call kt::value_method_helper_expects(e.methods(), trait_impls, type_name) %}
