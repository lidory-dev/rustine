
{#
// Kotlin's `enum class` construct doesn't support variants with associated data,
// but is a little nicer for consumers than its `sealed class` enum pattern.
// So, we switch here, using `enum class` for enums with no associated data
// and `sealed class` for the general case.
#}

{%- let should_generate_serializable = config.generate_serializable() && e|serializable_enum(ci) -%}
{%- let trait_impls = self::enum_trait_impls(ci, e) -%}

{%- if e.is_flat() %}

{%- call kt::docstring(e, 0) %}
{% match e.variant_discr_type() %}
{% when None %}
{% if should_generate_serializable %}@kotlinx.serialization.Serializable{% endif %}
{{ visibility() }}enum class {{ type_name }} {
    {% for variant in e.variants() -%}
    {%- call kt::docstring(variant, 4) %}
    {{ variant|variant_name(config) }}{% if loop.last %};{% else %},{% endif %}
    {%- endfor %}
    {%- for meth in e.methods() %}
{% call kt::value_method_decl(meth, 4) %}
    {%- endfor %}
    {%- call kt::value_trait_impls(trait_impls, type_name, "override", 4) %}
    {{ visibility() }}companion object
}
{% when Some(variant_discr_type) %}
{% if should_generate_serializable %}@kotlinx.serialization.Serializable{% endif %}
{{ visibility() }}enum class {{ type_name }}(public val value: {{ variant_discr_type|type_name(ci) }}) {
    {% for variant in e.variants() -%}
    {%- call kt::docstring(variant, 4) %}
    {{ variant|variant_name(config) }}({{ e|variant_discr_literal(loop.index0) }}){% if loop.last %};{% else %},{% endif %}
    {%- endfor %}
    {%- for meth in e.methods() %}
{% call kt::value_method_decl(meth, 4) %}
    {%- endfor %}
    {%- call kt::value_trait_impls(trait_impls, type_name, "override", 4) %}
    {{ visibility() }}companion object
}
{% endmatch %}
{% else %}

{%- let comparable = trait_impls.compare_to().is_some() -%}
{%- call kt::docstring(e, 0) %}
{% if should_generate_serializable %}@kotlinx.serialization.Serializable{% endif %}
{{ visibility() }}sealed class {{ type_name }}{% if contains_object_references && comparable %}: Disposable, Comparable<{{ type_name }}> {% elif contains_object_references %}: Disposable {% elif comparable %}: Comparable<{{ type_name }}> {% endif %} {
    {% for variant in e.variants() -%}
    {%- let variant_type_name = variant|variant_type_name(ci) -%}
    {%- let should_generate_variant_serializable = config.generate_serializable() && variant|serializable_enum_variant(ci) -%}
    {%- call kt::docstring(variant, 4) %}
    {%- if !variant.has_fields() %}
    {% if should_generate_variant_serializable %}@kotlinx.serialization.Serializable{% endif %}
    {{ visibility() }}{% if config.use_data_objects() %}data {% endif %}object {{ variant_type_name }} : {{ type_name }}() {% if contains_object_references %} {
        override fun destroy(): Unit = Unit
    }
    {% endif %}
    {% else -%}
    {%- let should_generate_equals_hash_code = variant|should_generate_equals_hash_code_enum_variant -%}
    {% if should_generate_variant_serializable %}@kotlinx.serialization.Serializable{% endif %}
    {{ visibility() }}data class {{ variant_type_name }}(
        {%- for field in variant.fields() -%}
        {%- call kt::docstring(field, 8) %}
        val {% call kt::field_name(field, loop.index) %}: {{ field|qualified_type_name(ci, config) -}}
        {%- match field.default_value() %}
        {%- when Some with(default) %} = {{ default|render_default(field, ci, config) }}
        {%- else %}
        {%- endmatch %},
        {%- endfor %}
    ) : {{ type_name }}() {
        {%- if should_generate_equals_hash_code -%}
        {#- The sealed class implements `equals()` and `hashCode()` when they come from uniffi traits. -#}
        {%- call kt::generate_equals_hash_code(variant, variant_type_name, 8, trait_impls.equals().is_none(), trait_impls.hash_code().is_none()) -%}
        {%- endif -%}
        {%- if contains_object_references %}
        override fun destroy() {
            {%- if variant.has_fields() -%}
            {%- call kt::destroy_fields(variant, 12) -%}
            {%- else %}
            // Nothing to destroy
            {%- endif %}
        }
        {%- endif %}
    }
    {%- endif %}
    {% endfor %}
    {%- for meth in e.methods() %}
{% call kt::value_method_decl(meth, 4) %}
    {%- endfor %}
    {#- Upstream overrides these members in each data class variant too. #}
    {%- call kt::value_trait_impls(trait_impls, type_name, "final override", 4) %}
}

{% endif %}
{%- call kt::value_method_helper_expects(e.methods(), trait_impls, type_name) %}
