"""
Simple Class-Based YAML Generator for TQ Framework.

Each check type has its own class that converts parameters to Soda YAML.
Uses Soda Core's built-in functions when possible.
"""

import yaml
from datetime import datetime
from pathlib import Path
from typing import List, Dict, Any
from core.sql_generator import SQLDialectFactory


# =============================================================================
# BASE CLASS
# =============================================================================

class SodaCheckGenerator:
    """
    Base class for generating Soda YAML checks.
    
    Each check type inherits from this and implements to_yaml().
    """
    
    def __init__(self, params: Dict[str, Any]):
        """
        Initialize with check parameters.
        
        Args:
            params: Dictionary with check parameters from database
        """
        self.params = params
        self.check_name = params['check_name']
        self.technical_key = params['technical_key']
        self.status = params.get('status', 'A')
        
    def to_yaml(self) -> str:
        """
        Convert parameters to Soda YAML.
        Must be implemented by subclasses.
        """
        raise NotImplementedError("Subclasses must implement to_yaml()")
    
    def _add_header(self, yaml_content: str) -> str:
        """Add header comment to YAML."""
        header = f"""# ============================================
# TQ Framework - Soda Core Check
# ============================================
# Check Name: {self.check_name}
# Technical Key: {self.technical_key}
# Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
# Status: {self.status}
# ============================================

"""
        return header + yaml_content
    
    def _to_yaml_string(self, data: Dict) -> str:
        """Convert dictionary to YAML string."""
        return yaml.dump(data, default_flow_style=False, sort_keys=False, width=1000)


# =============================================================================
# RECONCILIATION CHECK CLASS
# =============================================================================

class ReconciliationCheck(SodaCheckGenerator):
    """
    Generates Soda YAML for reconciliation checks.
    
    Supports three strategies:
    - 'count': Use Soda's row_count (simplest, recommended)
    - 'md5': Custom MD5 hash comparison
    - 'deepdiff': Row-by-row comparison
    """
    
    def __init__(self, params: Dict[str, Any]):
        super().__init__(params)
        
        # Extract parameters
        self.source_schema = params['source_schema']
        self.source_table = params['source_table']
        self.target_schema = params['target_schema']
        self.target_table = params['target_table']
        self.comp_strategy = params['comp_strategy']
        self.source_keys = [k.strip() for k in params['source_unique_key'].split(',')]
        self.target_keys = [k.strip() for k in params['target_unique_key'].split(',')]
        self.filters = params.get('filters')
        self.db_type = params['source_system_type']
        
        # Get SQL dialect
        self.dialect = SQLDialectFactory.get(self.db_type)
    
    def to_yaml(self) -> str:
        """Generate Soda YAML based on strategy."""
        
        if self.comp_strategy == 'count':
            yaml_content = self._generate_count_strategy()
        elif self.comp_strategy == 'md5':
            yaml_content = self._generate_md5_strategy()
        elif self.comp_strategy == 'deepdiff':
            yaml_content = self._generate_deepdiff_strategy()
        else:
            raise ValueError(f"Unknown strategy: {self.comp_strategy}")
        
        return self._add_header(yaml_content)
    
    def _generate_count_strategy(self) -> str:
        """
        Generate YAML using Soda's built-in row_count.
        This is the simplest and recommended approach.
        """
        source_table = f"{self.source_schema}.{self.source_table}"
        target_table = f"{self.target_schema}.{self.target_table}"
        
        # Use Soda Core's built-in row_count check
        soda_config = {
            'checks for': source_table,
            'checks': []
        }
        
        # Row count on source (with filter if provided)
        if self.filters:
            soda_config['checks'].append({
                f'row_count same as {target_table}': {
                    'filter': self.filters
                }
            })
        else:
            # Simple row count comparison
            soda_config['checks'].append(
                f'row_count same as {target_table}'
            )
        
        return self._to_yaml_string(soda_config)
    
    def _generate_md5_strategy(self) -> str:
        """Generate YAML using MD5 hash comparison."""
        
        source_table = f"{self.source_schema}.{self.source_table}"
        target_table = f"{self.target_schema}.{self.target_table}"
        
        # Generate MD5 expressions using dialect
        source_md5 = self.dialect.make_md5(self.source_keys)
        target_md5 = self.dialect.make_md5(self.target_keys)
        
        # Build comparison query
        where_clause = f"WHERE {self.filters}" if self.filters else ""
        
        query = f"""
WITH source_data AS (
    SELECT 
        {', '.join(self.source_keys)},
        {source_md5} as hash_value
    FROM {source_table}
    {where_clause}
),
target_data AS (
    SELECT 
        {', '.join(self.target_keys)},
        {target_md5} as hash_value
    FROM {target_table}
),
comparison AS (
    SELECT 
        COALESCE(s.hash_value, 'SOURCE_MISSING') as source_hash,
        COALESCE(t.hash_value, 'TARGET_MISSING') as target_hash,
        CASE 
            WHEN s.hash_value IS NULL THEN 'MISSING_IN_SOURCE'
            WHEN t.hash_value IS NULL THEN 'MISSING_IN_TARGET'
            WHEN s.hash_value != t.hash_value THEN 'HASH_MISMATCH'
            ELSE 'MATCH'
        END as match_status
    FROM source_data s
    FULL OUTER JOIN target_data t
        ON s.{self.source_keys[0]} = t.{self.target_keys[0]}
)
SELECT 
    COUNT(*) as mismatched_rows
FROM comparison
WHERE match_status != 'MATCH'
"""
        
        soda_config = {
            'checks for': source_table,
            'checks': [
                {
                    'name': self.check_name,
                    'query': query.strip(),
                    'tests': [
                        {'expression': 'mismatched_rows = 0'}
                    ]
                }
            ]
        }
        
        return self._to_yaml_string(soda_config)
    
    def _generate_deepdiff_strategy(self) -> str:
        """
        Generate YAML for deep row comparison.
        Uses Soda's schema and row_count checks.
        """
        
        source_table = f"{self.source_schema}.{self.source_table}"
        target_table = f"{self.target_schema}.{self.target_table}"
        
        soda_config = {
            'checks for': source_table,
            'checks': [
                # Check row counts match
                f'row_count same as {target_table}',
                # Check schemas match
                {
                    'schema': {
                        'warn': {
                            'when schema changes': []
                        },
                        'fail': {
                            'when required column missing': ['*']
                        }
                    }
                }
            ]
        }
        
        # Add filter if provided
        if self.filters:
            soda_config['filters'] = {
                self.source_table: self.filters
            }
        
        return self._to_yaml_string(soda_config)


# =============================================================================
# CROSS CHECK CLASS
# =============================================================================

class CrossCheck(SodaCheckGenerator):
    """
    Generates Soda YAML for cross checks.
    
    Compares row counts between two tables in the same system.
    Uses Soda's built-in row_count function.
    """
    
    def __init__(self, params: Dict[str, Any]):
        super().__init__(params)
        
        # Extract parameters
        self.source_schema = params['source_schema']
        self.source_table = params['source_table']
        self.target_schema = params['target_schema']
        self.target_table = params['target_table']
        self.filters = params.get('filters')
        self.db_type = params['source_system_type']
        self.tolerance_pct = params.get('tolerance_percentage', 0)
        self.tolerance_abs = params.get('tolerance_absolute', 0)
        
        # Get SQL dialect
        self.dialect = SQLDialectFactory.get(self.db_type)
    
    def to_yaml(self) -> str:
        """Generate Soda YAML for cross check."""
        
        source_table = f"{self.source_schema}.{self.source_table}"
        target_table = f"{self.target_schema}.{self.target_table}"
        
        # Use Soda's built-in row_count comparison when possible
        if self.tolerance_pct == 0 and self.tolerance_abs == 0:
            yaml_content = self._generate_exact_match(source_table, target_table)
        else:
            yaml_content = self._generate_with_tolerance(source_table, target_table)
        
        return self._add_header(yaml_content)
    
    def _generate_exact_match(self, source_table: str, target_table: str) -> str:
        """Generate YAML for exact row count match using Soda built-in."""
        
        soda_config = {
            'checks for': source_table,
            'checks': []
        }
        
        # Use Soda's built-in row_count comparison
        if self.filters:
            soda_config['checks'].append({
                f'row_count same as {target_table}': {
                    'name': self.check_name,
                    'filter': self.filters
                }
            })
        else:
            soda_config['checks'].append({
                f'row_count same as {target_table}': {
                    'name': self.check_name
                }
            })
        
        return self._to_yaml_string(soda_config)
    
    def _generate_with_tolerance(self, source_table: str, target_table: str) -> str:
        """Generate YAML with tolerance using custom query."""
        
        # Build count queries using dialect
        source_count = self.dialect.make_count(
            self.source_schema, 
            self.source_table, 
            self.filters
        )
        target_count = self.dialect.make_count(
            self.target_schema, 
            self.target_table
        )
        
        # Build comparison query with tolerance
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
            ROUND(ABS(s.row_count - t.row_count) * 100.0 / s.row_count, 2)
        ELSE 0
    END as variance_pct
FROM source s, target t
"""
        
        # Build validation expression
        validation = self._build_tolerance_expression()
        
        soda_config = {
            'checks for': source_table,
            'checks': [
                {
                    'name': self.check_name,
                    'query': query.strip(),
                    'tests': [
                        {'expression': validation}
                    ]
                }
            ]
        }
        
        return self._to_yaml_string(soda_config)
    
    def _build_tolerance_expression(self) -> str:
        """Build the tolerance validation expression."""
        
        if self.tolerance_pct > 0 and self.tolerance_abs > 0:
            # Either tolerance is acceptable
            return f"difference <= {self.tolerance_abs} OR variance_pct <= {self.tolerance_pct}"
        elif self.tolerance_pct > 0:
            # Only percentage tolerance
            return f"variance_pct <= {self.tolerance_pct}"
        elif self.tolerance_abs > 0:
            # Only absolute tolerance
            return f"difference <= {self.tolerance_abs}"
        else:
            # Exact match
            return "difference = 0"


# =============================================================================
# FACTORY - Creates the Right Check Class
# =============================================================================

class CheckYAMLFactory:
    """
    Factory to create the appropriate check generator.
    """
    
    # Map check types to classes
    _check_classes = {
        'TQ_RECONCILIATION_CHECK': ReconciliationCheck,
        'TQ_CROSS_CHECK': CrossCheck,
    }
    
    @classmethod
    def create(cls, params: Dict[str, Any]) -> SodaCheckGenerator:
        """
        Create the appropriate check generator.
        
        Args:
            params: Check parameters dictionary (must include 'check_type')
            
        Returns:
            Check generator instance
        """
        check_type = params.get('check_type')
        
        if not check_type:
            raise ValueError("'check_type' is required in parameters")
        
        check_class = cls._check_classes.get(check_type)
        
        if not check_class:
            supported = list(cls._check_classes.keys())
            raise ValueError(
                f"Unknown check type: {check_type}\n"
                f"Supported types: {supported}"
            )
        
        return check_class(params)
    
    @classmethod
    def register(cls, check_type: str, check_class: type):
        """Register a new check type."""
        cls._check_classes[check_type] = check_class


# =============================================================================
# MAIN FUNCTION - Process Multiple Checks
# =============================================================================

def generate_soda_yamls(checks: List[Dict[str, Any]], output_dir: str = 'soda_checks') -> List[str]:
    """
    Generate Soda YAML files from a list of check parameters.
    
    Args:
        checks: List of check parameter dictionaries
        output_dir: Directory to save YAML files
        
    Returns:
        List of generated file paths
        
    Example:
        checks = [
            {
                'check_type': 'TQ_RECONCILIATION_CHECK',
                'technical_key': 'RECON_001',
                'check_name': 'customer_recon',
                'source_schema': 'SALES',
                'source_table': 'CUSTOMERS',
                'source_system_type': 'oracle',
                'target_schema': 'DWH',
                'target_table': 'DIM_CUSTOMERS',
                'comp_strategy': 'count',
                'source_unique_key': 'customer_id',
                'target_unique_key': 'customer_key',
                'status': 'A'
            }
        ]
        
        files = generate_soda_yamls(checks, output_dir='soda_checks')
    """
    
    # Create output directory
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    
    generated_files = []
    
    for i, check_params in enumerate(checks, 1):
        try:
            # Skip inactive checks
            if check_params.get('status') == 'I':
                print(f"[{i}/{len(checks)}] Skipping inactive: {check_params.get('check_name', 'unknown')}")
                continue
            
            # Create check generator
            check = CheckYAMLFactory.create(check_params)
            
            # Generate YAML
            yaml_content = check.to_yaml()
            
            # Create filename
            safe_name = check.check_name.replace(' ', '_').replace('/', '_')
            filename = f"{safe_name}_{check.technical_key}.yml"
            filepath = output_path / filename
            
            # Write file
            with open(filepath, 'w') as f:
                f.write(yaml_content)
            
            print(f"[{i}/{len(checks)}] ✓ Generated: {filename}")
            generated_files.append(str(filepath))
            
        except Exception as e:
            check_name = check_params.get('check_name', 'unknown')
            print(f"[{i}/{len(checks)}] ✗ Failed {check_name}: {e}")
            continue
    
    return generated_files


# =============================================================================
# USAGE EXAMPLES
# =============================================================================

if __name__ == '__main__':
    
    print("=" * 70)
    print("TQ Framework - Class-Based YAML Generator")
    print("=" * 70)
    print()
    
    # Example checks list
    checks = [
        # Reconciliation check with COUNT strategy (uses Soda built-in)
        {
            'check_type': 'TQ_RECONCILIATION_CHECK',
            'technical_key': 'RECON_001',
            'check_name': 'customers_count_reconciliation',
            'source_schema': 'SALES',
            'source_table': 'CUSTOMERS',
            'source_system_type': 'oracle',
            'target_schema': 'DWH',
            'target_table': 'DIM_CUSTOMERS',
            'comp_strategy': 'count',  # Uses Soda's row_count
            'source_unique_key': 'customer_id',
            'target_unique_key': 'customer_key',
            'filters': 'active_flag = 1',
            'status': 'A'
        },
        
        # Reconciliation check with MD5 strategy
        {
            'check_type': 'TQ_RECONCILIATION_CHECK',
            'technical_key': 'RECON_002',
            'check_name': 'orders_md5_reconciliation',
            'source_schema': 'RAW',
            'source_table': 'ORDERS',
            'source_system_type': 'trino',
            'target_schema': 'CURATED',
            'target_table': 'ORDERS',
            'comp_strategy': 'md5',
            'source_unique_key': 'order_id, customer_id',
            'target_unique_key': 'order_key, customer_key',
            'status': 'A'
        },
        
        # Cross check with exact match (uses Soda built-in)
        {
            'check_type': 'TQ_CROSS_CHECK',
            'technical_key': 'CROSS_001',
            'check_name': 'staging_prod_exact',
            'source_schema': 'STAGING',
            'source_table': 'SALES',
            'source_system_type': 'postgresql',
            'target_schema': 'PRODUCTION',
            'target_table': 'SALES',
            'status': 'A'
        },
        
        # Cross check with tolerance
        {
            'check_type': 'TQ_CROSS_CHECK',
            'technical_key': 'CROSS_002',
            'check_name': 'staging_prod_with_tolerance',
            'source_schema': 'STAGING',
            'source_table': 'TRANSACTIONS',
            'source_system_type': 'oracle',
            'target_schema': 'PRODUCTION',
            'target_table': 'TRANSACTIONS',
            'tolerance_percentage': 5.0,
            'tolerance_absolute': 100,
            'filters': 'transaction_date = CURRENT_DATE',
            'status': 'A'
        },
        
        # Inactive check - will be skipped
        {
            'check_type': 'TQ_RECONCILIATION_CHECK',
            'technical_key': 'RECON_999',
            'check_name': 'inactive_check',
            'source_schema': 'TEST',
            'source_table': 'TEST',
            'source_system_type': 'oracle',
            'target_schema': 'TEST',
            'target_table': 'TEST',
            'comp_strategy': 'count',
            'source_unique_key': 'id',
            'target_unique_key': 'id',
            'status': 'I'  # Inactive
        }
    ]
    
    print(f"Processing {len(checks)} checks...")
    print()
    
    # Generate YAML files
    generated = generate_soda_yamls(checks, output_dir='example_soda_checks')
    
    print()
    print("=" * 70)
    print(f"✓ Successfully generated {len(generated)} YAML files")
    print("=" * 70)
    print()
    
    # Show what was generated
    if generated:
        print("Generated files:")
        for file in generated:
            print(f"  - {file}")
