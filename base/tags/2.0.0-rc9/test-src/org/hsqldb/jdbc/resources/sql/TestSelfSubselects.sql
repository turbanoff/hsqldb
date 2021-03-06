drop table colors if exists;
drop table sizes if exists;
drop table fruits if exists;
drop table trees if exists;
create table colors(id int, val char(20));
insert into colors values(1,'red');
insert into colors values(2,'green');
insert into colors values(3,'orange');
insert into colors values(4,'indigo');
create table sizes(id int, val char(20));
insert into sizes values(1,'small');
insert into sizes values(2,'medium');
insert into sizes values(3,'large');
insert into sizes values(4,'odd');
create table fruits(id int, name char(30), color_id int);
insert into fruits values(1, 'golden delicious',2);
insert into fruits values(2, 'macintosh',1);
insert into fruits values(3, 'red delicious',1);
insert into fruits values(4, 'granny smith',2);
insert into fruits values(5, 'tangerine',4);
create table trees(id int, name char(30), fruit_id int, size_id int);
insert into trees values(1, 'small golden delicious tree',1,1);
insert into trees values(2, 'large macintosh tree',2,3);
insert into trees values(3, 'large red delicious tree',3,3);
insert into trees values(4, 'small red delicious tree',3,1);
insert into trees values(5, 'medium granny smith tree',4,2);
select a.val, b.name from sizes a, trees b where a.id = b.size_id and b.id in (select a.id from trees a, fruits b where a.fruit_id = b.id and b.name='red delicious') order by a.val;
--
-- bug #547764
-- name in subquery must resolve to the subquery first
drop table trees if exists;
drop table fruits if exists;
create table trees(id integer primary key,name varchar(30) not null);
create table fruits(id integer primary key,name varchar(30) not null,
 tree_id integer not null,foreign key (tree_id) references trees(id));
insert into trees (id, name) values (1, 'apple');
insert into fruits (id, name, tree_id) values(1, 'pippin', 1);
insert into fruits (id, name, tree_id) values(2, 'granny smith', 1);
/*c2*/select id from fruits where tree_id in(select id from trees where name = 'apple');
