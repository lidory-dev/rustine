
{%- let rec = ci.get_record_definition(name).unwrap() -%}
{%- let trait_impls = self::record_trait_impls(rec) -%}
{%- let should_generate_equals_hash_code = rec|should_generate_equals_hash_code_record -%}
{%- let should_generate_serializable = config.generate_serializable() && rec|serializable_record(ci) -%}

{%- if rec.has_fields() %}
{%- let comparable = trait_impls.compare_to().is_some() -%}
{%- call kt::docstring(rec, 0) %}
{% if should_generate_serializable %}@kotlinx.serialization.Serializable{% endif %}
{{ visibility() }}data class {{ type_name }} (
    {%- for field in rec.fields() %}
    {%- call kt::docstring(field, 4) %}
    {% if config.generate_immutable_records() %}val{% else %}var{% endif %} {{ field.name()|var_name }}: {{ field|type_name(ci) -}}
    {%- match field.default_value() %}
        {%- when Some with(default) %} = {{ default|render_default(field, ci, config) }}
        {%- else %}
    {%- endmatch -%}
    {% if !loop.last %}, {% endif %}
    {%- endfor %}
) {% if contains_object_references && comparable %}: Disposable, Comparable<{{ type_name }}> {% elif contains_object_references %}: Disposable {% elif comparable %}: Comparable<{{ type_name }}> {% endif %}{
    {%- if should_generate_equals_hash_code -%}
    {%- call kt::generate_equals_hash_code(rec, type_name, 4, trait_impls.equals().is_none(), trait_impls.hash_code().is_none()) -%}
    {%- endif -%}
    {%- if contains_object_references %}
    override fun destroy() {
        {%- call kt::destroy_fields(rec, 8) %}
    }
    {%- endif %}
    {%- for meth in rec.methods() %}
{% call kt::value_method_decl(meth, 4) %}
    {%- endfor %}
    {%- call kt::value_trait_impls(trait_impls, type_name, "override", 4) %}
    {{ visibility() }}companion object
}
{%- else -%}
{%- call kt::docstring(rec, 0) %}
{{ visibility() }}{% if config.use_data_objects() %}data {% endif %}object {{ type_name }}
{%- if !rec.methods().is_empty() || trait_impls.to_string().is_some() %} {
    {%- for meth in rec.methods() %}
{% call kt::value_method_decl(meth, 4) %}
    {%- endfor %}
    {%- call kt::value_trait_impls(trait_impls, type_name, "override", 4) %}
}
{%- endif %}
{%- endif %}
{%- call kt::value_method_helper_expects(rec.methods(), trait_impls, type_name) %}
