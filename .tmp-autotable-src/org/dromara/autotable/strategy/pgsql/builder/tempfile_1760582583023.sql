SELECT con.conname AS primary_name,
       string_agg(col.attname, ',' ORDER BY col.attnum) AS columns
FROM pg_catalog.pg_constraint con
JOIN pg_catalog.pg_class cls ON con.conrelid = cls.oid
JOIN pg_catalog.pg_namespace nsp ON cls.relnamespace = nsp.oid
JOIN pg_catalog.unnest(con.conkey) WITH ORDINALITY AS attnum(attnum, ord)
JOIN pg_catalog.pg_attribute col ON col.attrelid = cls.oid AND col.attnum = attnum.attnum
WHERE nsp.nspname = ':schema'
  AND cls.relname = ':tableName'
  AND con.contype = 'p'
GROUP BY con.conname;
