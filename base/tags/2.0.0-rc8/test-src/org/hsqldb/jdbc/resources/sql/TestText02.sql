/*c2*/SELECT * FROM TMAIN;
/*c0*/SELECT * FROM TMAIN EXCEPT SELECT * FROM T;
/*c0*/SELECT * FROM T EXCEPT SELECT * FROM TMAIN;
/*c6*/SELECT * FROM TTMAIN;
/*c0*/SELECT * FROM TT EXCEPT SELECT * FROM TTMAIN;
/*c0*/SELECT * FROM TTMAIN EXCEPT SELECT * FROM TT;
SHUTDOWN
