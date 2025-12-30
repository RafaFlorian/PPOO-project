"""
Simple Parameter to Soda YAML Converter.

This file takes check parameters from your database tables and generates
Soda Core YAML files. One function per check type, easy to understand.
"""

import yaml
from datetime import datetime
from pathlib import Path
from core.sql_generator import SQLDialectFactory


# =============================================================================
# RECONCILIATION CHECK - Parameters to YAML
# =============================================================================

def reconciliation_to_yaml(params: dict) -> str:
    """
    Convert reconciliation check parameters to Soda YAML.
    
    Args:
        params: Dictionary with these keys:
            - technical_key: Unique check identifier
            - check_name: Name of the check
            - source_system: Source system name
            - source_schema: Source schema name
            - source_table: Source table name
            - source_system_type: Database type ('oracle', 'trino', 'postgresql')
            - target_schema: Target schema name
            - target_table: Target table name
            - comp_strategy: Comparison strategy ('md5', 'count', 'deepdiff')
            - source_unique_key: Source key columns (comma-separated)
            - target_unique_key: Target key columns (comma-separated)
            - filters: Optional WHERE clause
            - status: 'A' (active) or 'I' (inactive)
    
    Returns:
        Soda YAML as string
    """
    
    # Extract parameters
    check_name = params['check_name']
    source_schema = params['source_schema']
    source_table = params['source_table']
    target_schema = params['target_schema']
    target_table = params['target_table']
    comp_strategy = params['comp_strategy']
    source_keys = [k.strip() for k in params['source_unique_key'].split(',')]
    target_keys = [k.strip() for k in params['target_unique_key'].split(',')]
    filters = params.get('filters')
    db_type = params['source_system_type']
    
    # Get SQL dialect for this database
    dialect = SQLDialectFactory.get(db_type)
    
    # Build YAML based on strategy
    if comp_strategy == 'md5':
        yaml_content = _build_md5_yaml(
            check_name, source_schema, source_table, target_schema, target_table,
            source_keys, target_keys, filters, dialect
        )
    elif comp_strategy == 'count':
        yaml_content = _build_count_yaml(
            check_name, source_schema, source_table, target_schema, target_table,
            filters, dialect
        )
    elif comp_strategy == 'deepdiff':
        yaml_content = _build_deepdiff_yaml(
            check_name, source_schema, source_table, target_schema, target_table,
            filters
        )
    else:
        raise ValueError(f"Unknown strategy: {comp_strategy}")
    
    # Add header
    header = _build_header(params)
    
    return header + yaml_content


def _build_md5_yaml(check_name, source_schema, source_table, target_schema, 
                    target_table, source_keys, target_keys, filters, dialect):
    """Build YAML for MD5 comparison strategy."""
    
    # Generate MD5 expressions
    source_md5 = dialect.make_md5(source_keys)
    target_md5 = dialect.make_md5(target_keys)
    
    # Build comparison query
    source_full = f"{source_schema}.{source_table}"
    target_full = f"{target_schema}.{target_table}"
    
    where_clause = f"WHERE {filters}" if filters else ""
    
    query = f"""
WITH source_data AS (
    SELECT 
        {', '.join(source_keys)},
        {source_md5} as source_hash
    FROM {source_full}
    {where_clause}
),
target_data AS (
    SELECT 
        {', '.join(target_keys)},
        {target_md5} as target_hash
    FROM {target_full}
)
SELECT 
    COUNT(*) as mismatched_rows
FROM source_data s
FULL OUTER JOIN target_data t
    ON s.{source_keys[0]} = t.{target_keys[0]}
WHERE 
    s.source_hash IS NULL 
    OR t.target_hash IS NULL 
    OR s.source_hash != t.target_hash
"""
    
    # Build YAML structure
    soda_check = {
        'checks for': source_full,
        'checks': [
            {
                'name': check_name,
                'query': query.strip(),
                'tests': [
                    {'expression': 'mismatched_rows = 0'}
                ]
            }
        ]
    }
    
    return yaml.dump(soda_check, default_flow_style=False, sort_keys=False, width=1000)


def _build_count_yaml(check_name, source_schema, source_table, target_schema,
                      target_table, filters, dialect):
    """Build YAML for count comparison strategy."""
    
    # Generate count queries
    source_count = dialect.make_count(source_schema, source_table, filters)
    target_count = dialect.make_count(target_schema, target_table)
    
    # Build comparison query
    query = f"""
WITH source AS (
    {source_count}
),
target AS (
    {target_count}
)
SELECT 
    s.row_count as source_count,
    t.row_count as target_count,
    ABS(s.row_count - t.row_count) as difference
FROM source s, target t
"""
    
    # Build YAML structure
    source_full = f"{source_schema}.{source_table}"
    
    soda_check = {
        'checks for': source_full,
        'checks': [
            {
                'name': check_name,
                'query': query.strip(),
                'tests': [
                    {'expression': 'difference = 0'}
                ]
            }
        ]
    }
    
    return yaml.dump(soda_check, default_flow_style=False, sort_keys=False, width=1000)


def _build_deepdiff_yaml(check_name, source_schema, source_table, target_schema,
                         target_table, filters):
    """Build YAML for deep diff strategy."""
    
    source_full = f"{source_schema}.{source_table}"
    target_full = f"{target_schema}.{target_table}"
    
    where_clause = f" WHERE {filters}" if filters else ""
    
    # For deep diff, we compare row counts
    query = f"""
SELECT 
    (SELECT COUNT(*) FROM {source_full}{where_clause}) as source_count,
    (SELECT COUNT(*) FROM {target_full}) as target_count
"""
    
    soda_check = {
        'checks for': source_full,
        'checks': [
            {
                'name': f'{check_name}_row_count',
                'query': query.strip(),
                'tests': [
                    {'expression': 'source_count = target_count'}
                ]
            }
        ]
    }
    
    return yaml.dump(soda_check, default_flow_style=False, sort_keys=False, width=1000)


# =============================================================================
# CROSS CHECK - Parameters to YAML
# =============================================================================

def cross_check_to_yaml(params: dict) -> str:
    """
    Convert cross check parameters to Soda YAML.
    
    Args:
        params: Dictionary with these keys:
            - technical_key: Unique check identifier
            - check_name: Name of the check
            - source_schema: Source schema name
            - source_table: Source table name
            - source_system_type: Database type ('oracle', 'trino', 'postgresql')
            - target_schema: Target schema name
            - target_table: Target table name
            - tolerance_percentage: Allowed variance % (optional, default 0)
            - tolerance_absolute: Allowed absolute difference (optional, default 0)
            - filters: Optional WHERE clause
            - status: 'A' (active) or 'I' (inactive)
    
    Returns:
        Soda YAML as string
    """
    
    # Extract parameters
    check_name = params['check_name']
    source_schema = params['source_schema']
    source_table = params['source_table']
    target_schema = params['target_schema']
    target_table = params['target_table']
    filters = params.get('filters')
    db_type = params['source_system_type']
    tolerance_pct = params.get('tolerance_percentage', 0)
    tolerance_abs = params.get('tolerance_absolute', 0)
    
    # Get SQL dialect
    dialect = SQLDialectFactory.get(db_type)
    
    # Generate count queries
    source_count = dialect.make_count(source_schema, source_table, filters)
    target_count = dialect.make_count(target_schema, target_table)
    
    # Build comparison query
    query = f"""
WITH source AS (
    {source_count}
),
target AS (
    {target_count}
)
SELECT 
    s.row_count as source_count,
    t.row_count as target_count,
    ABS(s.row_count - t.row_count) as difference,
    CASE 
        WHEN s.row_count > 0 THEN 
            ABS(s.row_count - t.row_count) * 100.0 / s.row_count
        ELSE 0
    END as variance_pct
FROM source s, target t
"""
    
    # Build validation expression
    if tolerance_pct > 0 and tolerance_abs > 0:
        validation = f"difference <= {tolerance_abs} OR variance_pct <= {tolerance_pct}"
    elif tolerance_pct > 0:
        validation = f"variance_pct <= {tolerance_pct}"
    elif tolerance_abs > 0:
        validation = f"difference <= {tolerance_abs}"
    else:
        validation = "difference = 0"
    
    # Build YAML structure
    source_full = f"{source_schema}.{source_table}"
    
    soda_check = {
        'checks for': source_full,
        'checks': [
            {
                'name': check_name,
                'query': query.strip(),
                'tests': [
                    {'expression': validation}
                ]
            }
        ]
    }
    
    # Add header
    header = _build_header(params)
    
    return header + yaml.dump(soda_check, default_flow_style=False, sort_keys=False, width=1000)


# =============================================================================
# HELPERS
# =============================================================================

def _build_header(params: dict) -> str:
    """Build YAML header with metadata."""
    
    header = f"""# ============================================
# TQ Framework - Soda Core Check
# ============================================
# Check Name: {params['check_name']}
# Technical Key: {params['technical_key']}
# Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
# Status: {params.get('status', 'A')}
# ============================================

"""
    return header


# =============================================================================
# MAIN FUNCTION - Process Multiple Checks
# =============================================================================

def generate_yaml_files(checks: list, output_dir: str = 'soda_checks'):
    """
    Generate Soda YAML files from a list of check parameters.
    
    Args:
        checks: List of check parameter dictionaries
        output_dir: Directory to save YAML files
        
    Returns:
        List of generated file paths
    """
    
    # Create output directory
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    
    generated_files = []
    
    for check in checks:
        # Skip inactive checks
        if check.get('status') == 'I':
            print(f"Skipping inactive check: {check['check_name']}")
            continue
        
        # Determine check type
        check_type = check.get('check_type', 'TQ_RECONCILIATION_CHECK')
        
        # Generate YAML
        if check_type == 'TQ_RECONCILIATION_CHECK':
            yaml_content = reconciliation_to_yaml(check)
        elif check_type == 'TQ_CROSS_CHECK':
            yaml_content = cross_check_to_yaml(check)
        else:
            print(f"Unknown check type: {check_type}")
            continue
        
        # Generate filename
        safe_name = check['check_name'].replace(' ', '_').replace('/', '_')
        filename = f"{safe_name}_{check['technical_key']}.yml"
        filepath = output_path / filename
        
        # Write file
        with open(filepath, 'w') as f:
            f.write(yaml_content)
        
        print(f"✓ Generated: {filepath}")
        generated_files.append(str(filepath))
    
    return generated_files


# =============================================================================
# USAGE EXAMPLES
# =============================================================================

if __name__ == '__main__':
    
    print("=" * 60)
    print("TQ Framework - Simple YAML Generator")
    print("=" * 60)
    print()
    
    # Example 1: Single reconciliation check with MD5
    print("Example 1: Reconciliation Check (MD5 Strategy)")
    print("-" * 60)
    
    recon_check = {
        'check_type': 'TQ_RECONCILIATION_CHECK',
        'technical_key': 'RECON_001',
        'check_name': 'customer_reconciliation',
        'source_system': 'oracle_prod',
        'source_schema': 'SALES',
        'source_table': 'CUSTOMERS',
        'source_system_type': 'oracle',
        'target_schema': 'DWH',
        'target_table': 'DIM_CUSTOMERS',
        'comp_strategy': 'md5',
        'source_unique_key': 'customer_id, region_code',
        'target_unique_key': 'customer_key, region',
        'filters': 'active_flag = 1',
        'status': 'A'
    }
    
    yaml_output = reconciliation_to_yaml(recon_check)
    print(yaml_output)
    print()
    
    # Example 2: Cross check
    print("Example 2: Cross Check")
    print("-" * 60)
    
    cross_check = {
        'check_type': 'TQ_CROSS_CHECK',
        'technical_key': 'CROSS_001',
        'check_name': 'staging_to_prod_count',
        'source_schema': 'STAGING',
        'source_table': 'ORDERS',
        'source_system_type': 'trino',
        'target_schema': 'PRODUCTION',
        'target_table': 'ORDERS',
        'tolerance_percentage': 1.0,
        'tolerance_absolute': 10,
        'filters': 'order_date >= CURRENT_DATE - INTERVAL \'1\' DAY',
        'status': 'A'
    }
    
    yaml_output = cross_check_to_yaml(cross_check)
    print(yaml_output)
    print()
    
    # Example 3: Generate files for multiple checks
    print("Example 3: Generate Multiple YAML Files")
    print("-" * 60)
    
    all_checks = [
        {
            'check_type': 'TQ_RECONCILIATION_CHECK',
            'technical_key': 'RECON_001',
            'check_name': 'products_reconciliation',
            'source_schema': 'RAW',
            'source_table': 'PRODUCTS',
            'source_system_type': 'postgresql',
            'target_schema': 'CURATED',
            'target_table': 'PRODUCTS',
            'comp_strategy': 'count',
            'source_unique_key': 'product_id',
            'target_unique_key': 'product_id',
            'status': 'A'
        },
        {
            'check_type': 'TQ_CROSS_CHECK',
            'technical_key': 'CROSS_002',
            'check_name': 'daily_sales_volume',
            'source_schema': 'STAGING',
            'source_table': 'SALES',
            'source_system_type': 'oracle',
            'target_schema': 'MART',
            'target_table': 'SALES',
            'tolerance_percentage': 5.0,
            'status': 'A'
        },
        {
            'check_type': 'TQ_RECONCILIATION_CHECK',
            'technical_key': 'RECON_003',
            'check_name': 'inactive_check_example',
            'source_schema': 'TEST',
            'source_table': 'TEST_TABLE',
            'source_system_type': 'oracle',
            'target_schema': 'TEST',
            'target_table': 'TEST_TARGET',
            'comp_strategy': 'md5',
            'source_unique_key': 'id',
            'target_unique_key': 'id',
            'status': 'I'  # Inactive - will be skipped
        }
    ]
    
    generated = generate_yaml_files(all_checks, output_dir='example_checks')
    
    print()
    print(f"✓ Generated {len(generated)} YAML files")
    print()
    
    # Example 4: Load from your database
    print("Example 4: How to Use with Your Database")
    print("-" * 60)
    print("""
# Pseudo-code for loading from your metadata database:

import cx_Oracle  # or your database driver

# Connect to metadata database
connection = cx_Oracle.connect('user/pass@host/service')
cursor = connection.cursor()

# Query reconciliation checks
cursor.execute('''
    SELECT 
        'TQ_RECONCILIATION_CHECK' as check_type,
        TECHNICAL_KEY,
        CHECK_NAME,
        SOURCE_SYSTEM,
        SOURCE_SCHEMA,
        SOURCE_TABLE,
        SOURCE_SYSTEM_TYPE,
        TARGET_SCHEMA,
        TARGET_TABLE,
        COMP_STRATEGY,
        SOURCE_UNIQUE_KEY,
        TARGET_UNIQUE_KEY,
        FILTERS,
        STATUS
    FROM TQ_RECONCILIATION_CHECK
    WHERE STATUS = 'A'
''')

# Convert rows to dictionaries
checks = []
columns = [col[0].lower() for col in cursor.description]
for row in cursor:
    check = dict(zip(columns, row))
    checks.append(check)

# Generate YAML files
generated_files = generate_yaml_files(checks, output_dir='soda_checks')

print(f"Generated {len(generated_files)} YAML files")
""")
