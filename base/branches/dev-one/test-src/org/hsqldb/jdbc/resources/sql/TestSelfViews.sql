drop view v_my_server if exists;
drop table my_group if exists;
drop table dual if exists;
drop table my_server if exists;
-- test subselect in view
create cached table my_group (id integer, group_id integer);
insert into my_group values (1, 1);
insert into my_group values (2, 1);
insert into my_group values (3, 1);
insert into my_group values (4, 1);
create cached table dual (a integer);
insert into dual values (0);
create cached table my_server (group_id integer, server_id integer, weight integer);
insert into my_server values (-1, 1, 1);
insert into my_server values (-1, 2, 0);
insert into my_server values (100, 11, 1);
--
CREATE VIEW v_my_server AS SELECT DISTINCT mg.id, ms.server_id, ms.weight
 FROM (SELECT id, group_id FROM my_group
 UNION SELECT -1, -1 FROM DUAL) mg, my_server ms
 WHERE mg.group_id = ms.group_id;
--
/*c2*/select * from v_my_server;
/*c2*/select * from (select * from v_my_server);
-- test view in view
drop view v_test_view if exists;
CREATE VIEW v_test_view AS SELECT a.id, b.server_id, b.weight FROM v_my_server a JOIN v_my_server b ON a.id = b.id;
/*c4*/select * from v_test_view;
/*c4*/select * from (select * from v_test_view);
--
drop table colors if exists;
create table colors(id int, val char(10));
insert into colors values(1,'red');
insert into colors values(2,'green');
insert into colors values(3,'orange');
insert into colors values(4,'indigo');
--
drop view v_colors if exists;
create view v_colors(vid, vval) as select id, val from colors;
create view v_colors_o(vid, vval) as select id, val from colors order by id desc;
create view v_colors_o_l(vid, vval) as select id, val from colors order by id desc limit 1 offset 0;
create view v_colors_o_l_x(vid, vval) as select id, val from colors limit 1 offset 0;
/*e*/create view v_colors_o_l_x(vid, vval) as select id, val into newtable from colors;
--
/*c4*/select * from v_colors join v_test_view on server_id=vid;
/*c2*/select distinct * from v_test_view join v_colors on server_id=vid;
