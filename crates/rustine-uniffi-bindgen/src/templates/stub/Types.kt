
{%- import "macros.kt" as kt %}

{%- for type_ in ci.iter_local_types() %}
{%- let type_name = type_|type_name(ci) %}
{%- let ffi_converter_name = type_|ffi_converter_name %}
{%- let canonical_type_name = type_|canonical_name %}
{%- let contains_object_references = ci.item_contains_object_references(type_) %}

{#
 # Map `Type` instances to an include statement for that type.
 #
 # There is a companion match in `KotlinCodeOracle::create_code_type()` which performs a similar function for the
 # Rust code.
 #
 #   - When adding additional types here, make sure to also add a match arm to that function.
 #   - To keep things manageable, let's try to limit ourselves to these 2 mega-matches
 #}

{%- match type_ %}

{%- when Type::Object { module_path, name, imp } %}
{% include "ObjectTemplate.kt" %}

{%- when Type::Record { name, module_path } %}
{%- let rec = ci.get_record_definition(name).unwrap() %}
{%- let trait_impls = self::record_trait_impls(rec) %}
{%- call kt::value_method_helper_stubs(rec.methods(), trait_impls, type_name) %}

{%- when Type::Enum { name, module_path } %}
{%- let e = ci.get_enum_definition(name).unwrap() %}
{%- let trait_impls = self::enum_trait_impls(ci, e) %}
{%- call kt::value_method_helper_stubs(e.methods(), trait_impls, type_name) %}

{%- else %}
{%- endmatch %}
{%- endfor %}
