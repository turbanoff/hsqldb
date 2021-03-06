-- author Fred Toussi (fredt@users dot sourceforge.net) version 1.9.0

/*lob_schema_definition*/
CREATE SCHEMA SYSTEM_LOBS AUTHORIZATION DBA
 CREATE TABLE BLOCKS(BLOCK_ADDR INT, BLOCK_COUNT INT NOT NULL, TX_ID BIGINT NOT NULL,
   CONSTRAINT BLOCKS_PK PRIMARY KEY(BLOCK_ADDR))
 CREATE INDEX BLOCKS_IDX1 ON BLOCKS(BLOCK_COUNT)
 CREATE INDEX BLOCKS_IDX2 ON BLOCKS(TX_ID)
 CREATE TABLE LOBS(BLOCK_ADDR INT NOT NULL, BLOCK_COUNT INT NOT NULL, BLOCK_OFFSET INT, LOB_ID BIGINT,
   CONSTRAINT LOBS_PK PRIMARY KEY(LOB_ID, BLOCK_OFFSET),
   CONSTRAINT LOBS_UQ1 UNIQUE(LOB_ID, BLOCK_ADDR),  CONSTRAINT LOBS_UQ2 UNIQUE(BLOCK_ADDR) )
 CREATE INDEX LOBS_IDX1 ON LOBS(LOB_ID, BLOCK_COUNT)
 CREATE TABLE LOB_IDS(LOB_ID BIGINT, LOB_LENGTH BIGINT NOT NULL, LOB_USAGE_COUNT INT DEFAULT 0, LOB_TYPE SMALLINT NOT NULL,
   CONSTRAINT LOB_IDS_PK PRIMARY KEY(LOB_ID))
 CREATE INDEX LOBS_IDX2 ON LOB_IDS(LOB_USAGE_COUNT)
 CREATE SEQUENCE LOB_ID AS BIGINT START WITH 1

 CREATE PROCEDURE CONVERT_BLOCK(B_ADDR INT, B_COUNT INT, B_OFFSET INT, L_ID BIGINT)
  MODIFIES SQL DATA BEGIN ATOMIC
  DELETE FROM BLOCKS WHERE BLOCK_ADDR = B_ADDR;
  INSERT INTO LOBS VALUES(B_ADDR, B_COUNT, B_OFFSET, L_ID);
 END

 CREATE PROCEDURE DELETE_LOB(L_ID BIGINT, TX_ID BIGINT)
  MODIFIES SQL DATA BEGIN ATOMIC
  INSERT INTO BLOCKS (SELECT BLOCK_ADDR,BLOCK_COUNT,TX_ID FROM LOBS WHERE LOB_ID = L_ID);
  DELETE FROM LOBS WHERE LOB_ID = L_ID;
  DELETE FROM LOB_IDS WHERE LOB_ID = L_ID;
 END

 CREATE PROCEDURE DELETE_UNUSED()
  MODIFIES SQL DATA BEGIN ATOMIC
  INSERT INTO BLOCKS (SELECT BLOCK_ADDR,BLOCK_COUNT,0 FROM LOBS WHERE LOB_ID
   IN (SELECT LOB_ID FROM LOB_IDS WHERE LOB_USAGE_COUNT < 1));
  DELETE FROM LOBS WHERE LOB_ID
   IN (SELECT LOB_ID FROM LOB_IDS WHERE LOB_USAGE_COUNT < 1);
  DELETE FROM LOB_IDS WHERE LOB_USAGE_COUNT < 1;
 END

 CREATE PROCEDURE DELETE_BLOCKS(L_ID BIGINT, B_OFFSET INT, B_LIMIT INT, TX_ID BIGINT)
  MODIFIES SQL DATA BEGIN ATOMIC

  INSERT INTO BLOCKS (SELECT BLOCK_ADDR,BLOCK_COUNT,TX_ID FROM LOBS
   WHERE LOB_ID = L_ID AND BLOCK_OFFSET >= B_OFFSET AND BLOCK_OFFSET < B_LIMIT);
  DELETE FROM LOBS
   WHERE LOB_ID = L_ID AND BLOCK_OFFSET >= B_OFFSET AND BLOCK_OFFSET < B_LIMIT;
 END

 CREATE PROCEDURE CREATE_EMPTY_BLOCK(INOUT B_ADDR INT, IN B_COUNT INT)
  MODIFIES SQL DATA BEGIN ATOMIC
  DECLARE TEMP_COUNT INT DEFAULT NULL;
  DECLARE TEMP_ADDR INT DEFAULT NULL;
  SET (TEMP_ADDR, TEMP_COUNT) = (SELECT BLOCK_ADDR, BLOCK_COUNT FROM BLOCKS WHERE BLOCK_COUNT > B_COUNT AND TX_ID = 0 FETCH 1 ROW ONLY);

  IF TEMP_ADDR IS NULL THEN
   SIGNAL SQLSTATE '45000';
  END IF;

  UPDATE BLOCKS SET BLOCK_COUNT = B_COUNT WHERE BLOCK_ADDR = TEMP_ADDR;
  INSERT INTO BLOCKS VALUES (TEMP_ADDR + B_COUNT, TEMP_COUNT - B_COUNT, 0);
  SET B_ADDR = TEMP_ADDR;
 END

 CREATE PROCEDURE DIVIDE_BLOCK(B_OFFSET INT, L_ID BIGINT)
  MODIFIES SQL DATA BEGIN ATOMIC
  DECLARE BL_ADDR INT DEFAULT NULL;
  DECLARE BL_COUNT INT DEFAULT NULL;
  DECLARE BL_OFFSET INT DEFAULT NULL;

  SET (BL_ADDR, BL_COUNT, BL_OFFSET) = (SELECT BLOCK_ADDR, BLOCK_COUNT, BLOCK_OFFSET FROM LOBS WHERE LOB_ID = L_ID AND B_OFFSET > BLOCK_OFFSET AND B_OFFSET < BLOCK_OFFSET + BLOCK_COUNT);

   IF BL_ADDR IS NULL THEN
    SIGNAL SQLSTATE '45000';
   END IF;
  DELETE FROM LOBS WHERE BLOCK_ADDR = BL_ADDR;
  INSERT INTO LOBS VALUES (BL_ADDR, B_OFFSET - BL_OFFSET, BL_OFFSET, L_ID);
  INSERT INTO LOBS VALUES (BL_ADDR + B_OFFSET - BL_OFFSET, BL_OFFSET + BL_COUNT - B_OFFSET, B_OFFSET, L_ID);

 END

 CREATE PROCEDURE ALLOC_BLOCKS (IN B_COUNT INT, IN B_OFFSET INT, IN L_ID BIGINT)
  MODIFIES SQL DATA BEGIN ATOMIC

  DECLARE LOB_ADDR INT DEFAULT NULL;
  DECLARE REMAINING_COUNT INT DEFAULT 0;
  DECLARE BL_ADDR INT DEFAULT NULL;
  DECLARE TEMP_COUNT INT DEFAULT 0;
  DECLARE BL_OFFSET INT DEFAULT 0;

  SET REMAINING_COUNT = B_COUNT;
  SET BL_OFFSET = B_OFFSET;

  MAIN_LOOP: LOOP

   SET BL_ADDR = (SELECT BLOCK_ADDR FROM BLOCKS WHERE BLOCK_COUNT = B_COUNT AND TX_ID = 0 FETCH 1 ROW ONLY);

   IF BL_ADDR IS NOT NULL THEN

    CALL CONVERT_BLOCK (BL_ADDR, REMAINING_COUNT, BL_OFFSET, L_ID);

    IF LOB_ADDR IS NULL THEN
     SET LOB_ADDR = BL_ADDR;
    END IF;

    LEAVE MAIN_LOOP;

   END IF;

   SET (BL_ADDR, TEMP_COUNT) = (SELECT BLOCK_ADDR, BLOCK_COUNT FROM BLOCKS WHERE BLOCK_COUNT < B_COUNT AND TX_ID = 0 FETCH 1 ROW ONLY);

   IF BL_ADDR IS NOT NULL THEN

    CALL CONVERT_BLOCK (BL_ADDR, REMAINING_COUNT, BL_OFFSET, L_ID);

    IF LOB_ADDR IS NULL THEN
      SET LOB_ADDR = BL_ADDR;
    END IF;

    SET REMAINING_COUNT = REMAINING_COUNT - TEMP_COUNT;
    SET BL_OFFSET = BL_OFFSET + TEMP_COUNT;
    SET BL_ADDR = NULL;
    SET TEMP_COUNT = 0;

   ELSE

    CALL CREATE_EMPTY_BLOCK (BL_ADDR, REMAINING_COUNT);
    CALL CONVERT_BLOCK (BL_ADDR, REMAINING_COUNT, BL_OFFSET, L_ID);

    IF LOB_ADDR IS NULL THEN
     SET LOB_ADDR = BL_ADDR;

     LEAVE MAIN_LOOP;
    END IF;

   END IF;

  END LOOP MAIN_LOOP;
 END
;

/*get_lob_query*/
SELECT * FROM LOBS WHERE LOB_ID = ?;
/*get_lob_part_statement*/
SELECT * FROM LOBS WHERE LOB_ID = ? ORDER BY BLOCK_OFFSET OFFSET ? FETCH ? ROWS ONLY;
