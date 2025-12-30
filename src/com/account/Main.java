"""
SQL Generator with Strategy Pattern - Simplified Version.

This module generates database-specific SQL for different systems.
Each database has its own "dialect" class with custom SQL syntax.
"""

from abc import ABC, abstractmethod
from typing import List
from utils import get_logger, UnsupportedDatabaseException

logger = get_logger(__name__)


# =============================================================================
# STRATEGY PATTERN - Base Class
# =============================================================================

class SQLDialect(ABC):
    """
    Base class for all database dialects.
    Each database implements its own version of these methods.
    """
    
    @abstractmethod
    def make_md5(self, columns: List[str]) -> str:
        """Generate MD5 hash SQL for the given columns."""
        pass
    
    @abstractmethod
    def make_concat(self, columns: List[str]) -> str:
        """Generate concatenation SQL for the given columns."""
        pass
    
    @abstractmethod
    def make_count(self, schema: str, table: str, where: str = None) -> str:
        """Generate row count SQL."""
        pass
    
    def make_table_name(self, schema: str, table: str) -> str:
        """Generate fully qualified table name."""
        return f"{schema}.{table}"


# =============================================================================
# CONCRETE IMPLEMENTATIONS - One per Database
# =============================================================================

class OracleDialect(SQLDialect):
    """Oracle-specific SQL generation."""
    
    def make_md5(self, columns: List[str]) -> str:
        """
        Oracle uses DBMS_CRYPTO for MD5.
        Example: LOWER(RAWTOHEX(DBMS_CRYPTO.HASH(...)))
        """
        # Concatenate columns with separator
        concat = " || '|' || ".join([f"NVL(TRIM({col}), '')" for col in columns])
        
        # Wrap in Oracle's MD5 function
        return f"LOWER(RAWTOHEX(DBMS_CRYPTO.HASH(UTL_RAW.CAST_TO_RAW({concat}), 2)))"
    
    def make_concat(self, columns: List[str]) -> str:
        """
        Oracle uses || for concatenation.
        Example: col1 || '|' || col2
        """
        return " || '|' || ".join([f"NVL(TRIM({col}), '')" for col in columns])
    
    def make_count(self, schema: str, table: str, where: str = None) -> str:
        """Generate Oracle count query."""
        table_name = self.make_table_name(schema, table)
        query = f"SELECT COUNT(*) as row_count FROM {table_name}"
        
        if where:
            query += f" WHERE {where}"
        
        return query


class TrinoDialect(SQLDialect):
    """Trino-specific SQL generation."""
    
    def make_md5(self, columns: List[str]) -> str:
        """
        Trino uses MD5 and TO_UTF8 functions.
        Example: LOWER(TO_HEX(MD5(TO_UTF8(...))))
        """
        # Concatenate columns with separator
        concat = " || '|' || ".join([f"COALESCE(TRIM({col}), '')" for col in columns])
        
        # Wrap in Trino's MD5 function
        return f"LOWER(TO_HEX(MD5(TO_UTF8({concat}))))"
    
    def make_concat(self, columns: List[str]) -> str:
        """
        Trino uses || for concatenation.
        Example: col1 || '|' || col2
        """
        return " || '|' || ".join([f"COALESCE(TRIM({col}), '')" for col in columns])
    
    def make_count(self, schema: str, table: str, where: str = None) -> str:
        """Generate Trino count query."""
        table_name = self.make_table_name(schema, table)
        query = f"SELECT COUNT(*) as row_count FROM {table_name}"
        
        if where:
            query += f" WHERE {where}"
        
        return query


class PostgreSQLDialect(SQLDialect):
    """PostgreSQL-specific SQL generation."""
    
    def make_md5(self, columns: List[str]) -> str:
        """
        PostgreSQL has built-in MD5 function.
        Example: MD5(col1 || '|' || col2)
        """
        # Concatenate columns with separator
        concat = " || '|' || ".join([f"COALESCE(TRIM({col}), '')" for col in columns])
        
        # Wrap in PostgreSQL's MD5 function
        return f"MD5({concat})"
    
    def make_concat(self, columns: List[str]) -> str:
        """
        PostgreSQL uses || for concatenation.
        Example: col1 || '|' || col2
        """
        return " || '|' || ".join([f"COALESCE(TRIM({col}), '')" for col in columns])
    
    def make_count(self, schema: str, table: str, where: str = None) -> str:
        """Generate PostgreSQL count query."""
        table_name = self.make_table_name(schema, table)
        query = f"SELECT COUNT(*) as row_count FROM {table_name}"
        
        if where:
            query += f" WHERE {where}"
        
        return query


# =============================================================================
# FACTORY - Gets the Right Dialect for Your Database
# =============================================================================

class SQLDialectFactory:
    """
    Factory to get the correct SQL dialect for your database.
    
    Usage:
        dialect = SQLDialectFactory.get('oracle')
        md5_sql = dialect.make_md5(['customer_id', 'order_id'])
    """
    
    # Map database names to their dialect classes
    _dialects = {
        'oracle': OracleDialect,
        'trino': TrinoDialect,
        'postgresql': PostgreSQLDialect,
        'postgres': PostgreSQLDialect,  # Alias
    }
    
    @classmethod
    def get(cls, database_type: str) -> SQLDialect:
        """
        Get the SQL dialect for a database type.
        
        Args:
            database_type: Name of database ('oracle', 'trino', 'postgresql')
            
        Returns:
            SQL dialect instance
            
        Example:
            dialect = SQLDialectFactory.get('oracle')
            sql = dialect.make_md5(['col1', 'col2'])
        """
        db_type = database_type.lower()
        
        dialect_class = cls._dialects.get(db_type)
        
        if not dialect_class:
            supported = list(cls._dialects.keys())
            raise UnsupportedDatabaseException(
                f"Database '{database_type}' not supported. "
                f"Supported: {supported}"
            )
        
        logger.debug(f"Created SQL dialect for {database_type}")
        return dialect_class()
    
    @classmethod
    def add(cls, database_type: str, dialect_class: type):
        """
        Add support for a new database type.
        
        Args:
            database_type: Name of the database
            dialect_class: Class that implements SQLDialect
            
        Example:
            class MySQLDialect(SQLDialect):
                def make_md5(self, columns):
                    return f"MD5(CONCAT({', '.join(columns)}))"
                # ... implement other methods
            
            SQLDialectFactory.add('mysql', MySQLDialect)
        """
        cls._dialects[database_type.lower()] = dialect_class
        logger.info(f"Added SQL dialect for {database_type}")


# =============================================================================
# USAGE EXAMPLES (for reference)
# =============================================================================

"""
Example 1: Generate MD5 hash for Oracle
----------------------------------------
dialect = SQLDialectFactory.get('oracle')
md5_sql = dialect.make_md5(['customer_id', 'order_date'])

Result:
LOWER(RAWTOHEX(DBMS_CRYPTO.HASH(
    UTL_RAW.CAST_TO_RAW(
        NVL(TRIM(customer_id), '') || '|' || NVL(TRIM(order_date), '')
    ), 2
)))


Example 2: Generate count query for Trino
------------------------------------------
dialect = SQLDialectFactory.get('trino')
count_sql = dialect.make_count('sales', 'orders', 'status = 1')

Result:
SELECT COUNT(*) as row_count FROM sales.orders WHERE status = 1


Example 3: Add a new database
------------------------------
class SnowflakeDialect(SQLDialect):
    def make_md5(self, columns):
        concat = " || '|' || ".join([f"COALESCE(TRIM({col}), '')" for col in columns])
        return f"MD5({concat})"
    
    def make_concat(self, columns):
        return " || '|' || ".join([f"COALESCE(TRIM({col}), '')" for col in columns])
    
    def make_count(self, schema, table, where=None):
        query = f"SELECT COUNT(*) as row_count FROM {schema}.{table}"
        if where:
            query += f" WHERE {where}"
        return query

SQLDialectFactory.add('snowflake', SnowflakeDialect)
dialect = SQLDialectFactory.get('snowflake')


Example 4: Use in your check code
----------------------------------
# In reconciliation_check.py
dialect = SQLDialectFactory.get(source_system_type)  # e.g., 'oracle'

# Generate MD5 for source columns
source_md5 = dialect.make_md5(['customer_id', 'order_id'])

# Generate MD5 for target columns  
target_md5 = dialect.make_md5(['cust_key', 'ord_key'])

# Use in your reconciliation query
query = f'''
    SELECT 
        {source_md5} as source_hash
    FROM sales.orders
'''
"""
