
{%- let rec = ci.get_record_definition(name).unwrap() %}
{%- let trait_impls = self::record_trait_impls(rec) %}

{{ visibility() }}object {{ rec|ffi_converter_name }}: FfiConverterRustBuffer<{{ type_name }}> {
    override fun read(buf: ByteBuffer): {{ type_name }} {
        {%- if rec.has_fields() %}
        return {{ type_name }}(
        {%- for field in rec.fields() %}
            {{ field|read_fn(ci) }}(buf),
        {%- endfor %}
        )
        {%- else %}
        return {{ type_name }}
        {%- endif %}
    }

    override fun allocationSize(value: {{ type_name }}): ULong = {%- if rec.has_fields() %} (
        {%- for field in rec.fields() %}
            {{ field|allocation_size_fn }}(value.{{ field.name()|var_name }}){% if !loop.last %} +{% endif %}
        {%- endfor %}
    ) {%- else %} 0UL {%- endif %}

    override fun write(value: {{ type_name }}, buf: ByteBuffer) {
        {%- for field in rec.fields() %}
        {{ field|write_fn(ci) }}(value.{{ field.name()|var_name }}, buf)
        {%- endfor %}
    }
}
{%- call kt::value_method_helper_actuals(rec.methods(), trait_impls, type_name) %}
