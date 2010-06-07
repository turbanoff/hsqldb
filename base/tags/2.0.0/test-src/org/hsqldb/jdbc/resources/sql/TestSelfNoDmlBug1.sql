-- Tests a bug where with write_delay of 0, DML is not persisted until and
-- if DML is committed or DB is "shutdown".  If DB stops with no shutdown,
-- all work is lost.

--/*u0*/SET write_delay 0;
/*u0*/CREATE SCHEMA ghostSchema AUTHORIZATION dba;
/*u0*/SET SCHEMA ghostSchema;
/*u0*/CREATE TABLE t1(i int);
/*u0*/CREATE VIEW v1 AS SELECT * FROM t1;
/*c0*/SELECT * FROM v1;
/*c0*/SELECT * FROM t1;
/*u0*/COMMIT;
