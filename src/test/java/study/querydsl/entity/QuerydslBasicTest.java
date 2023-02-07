package study.querydsl.entity;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import jakarta.transaction.Transactional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;

import java.util.List;

import static com.querydsl.jpa.JPAExpressions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {
    @Autowired
    EntityManager em;
    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before(){
        queryFactory = new JPAQueryFactory(em);
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 10, teamB);
        Member member4 = new Member("member4", 20, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void startJPQL(){
        //member1을 찾아라.
        String qlString =
                "select m from Member m " +
                "where m.username = :username";

        Member findMember = em.createQuery(qlString, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void startQuerydsl(){
// 필드레벨로 보내버리고 위와 같은 설정을 하면 중복코드 제거가능하다.
//        JPAQueryFactory queryFactory = new JPAQueryFactory(em);
//        QMember m = new QMember("m");
        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void search(){
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1").and(member.age.between(10, 30)))
                .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void searchAndParam(){
        Member findMember = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1"),
                        member.age.between(10,30)
                )
                .fetchOne();
        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    /**
     * 결과 조회
     * */
    @Test
    public void resultFetch(){
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();

        Member fetchOne = queryFactory
                .selectFrom(QMember.member)
                .fetchOne();

        Member fetchFirst = queryFactory
                .selectFrom(member)
//                .limit(1).fetchOne() 아래와 동일함.
                .fetchFirst();

        /**
         *         QueryResults<Member> results = queryFactory
         *                 .selectFrom(member)
         *                 .fetchResults();
         *
         *         results.getTotal();
         *         //getTotal :::
         *             //Get the total number of results
         *             //Returns:
         *             //total rows
         *         List<Member> content = results.getResults();
         *
         *         long total = queryFactory
         *              .selectFrom(member)
         *              .fetchCount();
         *
         *         fetchResult와 fetchCount 는 Deprecated 되었기 떄문에 count query를 따로 직접 작성하는것이 바람직할 것이다.
         * */
    }

    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순 (desc)
     * 2. 회원 이름 오름차순 (asc)
     * 단 회원 이름 오름차순에 회원 이름이 없으면 마지막에 출력 (null last) @Id 가 아니므로 null인 경우도 있기 때문.
     *
     * */
    @Test
    public void sort(){
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(
                        member.age.desc(),
                        member.username.asc().nullsLast()
                )
                .fetch();

        // 오름차순(asc)이고 nullsLast 이므로 List<Member>result 의 0번째 데이터는 member5 ~ null 순으로 갈 것으로 예상됨.
        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isEqualTo(null);
    }

    /**
     * 페이징
     * */
    @Test
    public void paging1(){
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1) //몇번째부터 시작할 것인가?
                .limit(2)
                .fetch();

        assertThat(result.size()).isEqualTo(2);
    }
    @Test
    public void paging2(){
        QueryResults<Member> queryResults = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1) //몇번째부터 시작할 것인가?
                .limit(2)
                .fetchResults();
        //단순한 쿼리이기 때문에 deprecated된 fetchResults()를 사용했다.

        assertThat(queryResults.getTotal()).isEqualTo(4);
        assertThat(queryResults.getLimit()).isEqualTo(2);
        assertThat(queryResults.getOffset()).isEqualTo(1);
        assertThat(queryResults.getResults().size()).isEqualTo(2); //limit이 2개로 설정이 되어있기때문에 contents가 2개
    }

    @Test
    public void paging3(){
        List<Member> content = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1) //몇번째부터 시작할 것인가?
                .limit(2)
                .fetch();

        //count Query의 올바른 사용
        int totalSize = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .fetch().size();
        assertThat(totalSize).isEqualTo(4);
    }

    /**
     * 집합
     * */
    @Test
    public void aggregation(){
        List<Tuple> result = queryFactory
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(60);
        assertThat(tuple.get(member.age.avg())).isEqualTo(15);
        assertThat(tuple.get(member.age.max())).isEqualTo(20);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /**
     * 팀의 이름과 각 팀의 평균 연령을 구해라
     * */
    @Test
    public void group() throws Exception{
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamB.get(team.name)).isEqualTo("teamB");

        assertThat(teamA.get(member.age.avg())).isEqualTo(15);
        assertThat(teamB.get(member.age.avg())).isEqualTo(15);
    }

    /**
     * Join 기본
     * teamA에 소속된 모든 멤버 조회
     * */
    @Test
    public void join(){
        List<Member> result = queryFactory
                .selectFrom(member)
                //연관관계의 주인인 Member 엔티티의 team 과 Team 엔티티의의 @OneToMany 연결인 members, 여기선 team이라고 명명됨.
                //따로 명시하지 않고 그냥 join으로 입력하면 inner join 해버림.
                .join(member.team, team)
//                .leftJoin(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }


    /**
     * 세타 조인
     * 회원의 이름이 팀 이름과 같은 회원 조회
     * */
    @Test
    public void theta_join(){
        em.persist(new Member("teamA")); //회원의 이름이 teamA
        em.persist(new Member("teamB")); //회원의 이름이 teamB
        em.persist(new Member("teamC")); //회원의 이름이 teamB

        //막조인 : 연관관계가 없는 두 엔티티 조인?
        List<Member> result = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }


    /**
     * 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL : select m, t from Member m left join m.team t on t.name = 'teamA'
     * */
    @Test
    public void join_on_filtering(){
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 연관관계가 없는 엔티티 Outer join
     *      회원의 이름과 팀의 이름이 같은 대상 외부조인
     *      JPQL : select m, t From Member m LEFT JOIN Team t on m.username = t.name
     * */
    @Test
    public void join_on_no_relation(){
        em.persist(new Member("teamA")); //회원의 이름이 teamA
        em.persist(new Member("teamB")); //회원의 이름이 teamB
        em.persist(new Member("teamC")); //회원의 이름이 teamB

        //막조인 : 연관관계가 없는 두 엔티티 조인?
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
        /**
         * 주의! 문법을 잘 봐야 한다. leftJoin() 부분에 일반 조인과 다르게 엔티티 하나만 들어간다.
         * 일반조인: leftJoin(member.team, team)
         * on조인: from(member).leftJoin(team).on(xxx
         * */
    }


    /**
     * =================================================================================================================
     * fetch join
     *      fetch join은 (SQL 에서 제공하는 기능은 아니다) SQL의 조인을 활용해서 연관된 엔티티를 SQL로 한번에 조회하는 기능이며
     *      주로 성능 최적화에 많이 사용하는 방법
     * =================================================================================================================
     * */


    @PersistenceUnit
    EntityManagerFactory emf;
    //fetchJoin 미적용
    @Test
    public void fetchJoinNoUse(){
        em.flush();
        em.clear();
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        //이 상태는 Member 의 team은 lazy Loading 이기 떄문에 멤버만 조회되고 team은 조회되지 않는다.
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("패치 조인 미적용").isFalse();
    }

    @Test
    public void fetchJoinUse(){
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("패치 조인 적용").isTrue();
    }

    /**
     * =================================================================================================================
     * 서브쿼리
     *      from 절에서 서브쿼리를 사용하는 이유
     *      SQL은 데이터를 가져오는것에 집중해야하는데 화면에 필요한 데이터를 맞추다보면 from절의 depth가 엄청 커진다.
     *      그래서 db에서 퍼올리는 데이터를 줄이기 위해 사용한다.
     *
     * */

    //SubQuery ::: 1. 나이가 가장 많은 회원 조회
    @Test
    public void subQuery(){

        //Alias가 겹치면 안된다.
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(20, 20);
    }

    //SubQuery 2-1.나이가 평균 이상인 회원
    @Test
    public void subQueryGoe(){
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();
        assertThat(result).extracting("age")
                .containsExactly(20, 20);
    }

    //SubQuery 3.회원의 나이가 10살 이상인 회원들
    @Test
    public void subQueryIn(){
        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();
        assertThat(result).extracting("age")
                .containsExactly(20, 20);
    }

    @Test
    public void selectSubQuery(){
        QMember memberSub = new QMember("memberSub");
        List<Tuple> result = queryFactory
                .select(member.username,
                        //JPAExpressions Static import 함.
                        select(memberSub.age.avg())
                                .from(memberSub)
                )
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }
    /**
     * from 절의 서브쿼리 한계
     *      JPA JPQL 서브쿼리의 한계점으로 from 절의 서브쿼리(인라인 뷰)는 지원하지 않는다.
     *      당연히 Querydsl도 지원하지 않는다. 하이버네이트 구현체를 사용하면 select절의 서브쿼리는 지원한다.
     *      Querydsl도 하이버네이트 구현체를 사용하면 select절의 서브쿼리를 지원한다.
     *
     *해결방안
     *      1.서브쿼리를 join으로 변경한다. (가능한 상황도 있고, 불가능한 상황도 있다.)
     *      2.애플리케이션에서 쿼리를 2번 분리해서 실행한다.
     *      3.nativeSQL을 사용한다. (최후의 수단)
     * */




    /**
     * =================================================================================================================
     * Case 문
     * =================================================================================================================
     * */
    @Test
    public void basicCase(){
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타")
                )
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void complexCase(){
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 15)).then("잼민")
                        .when(member.age.between(15, 20)).then("학생")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }


    /**
     * 상수, 문자 더하기
     * */
    @Test
    public void constant() throws Exception{
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void concat() throws Exception{
        List<String> result = queryFactory
                // member.age는 String 타입이 아니라서 .stringValue()를 통해서 String 타입으로 변환한뒤에 concat으로 합쳐버렸다.
                // 아래와 같은 방법은 ENUM타입을 처리할때도 유용하게 사용된다.
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }


    @Test
    public void simpleProjection() throws Exception{
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void tupleProjection() throws Exception{
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();
        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username = " + username);
            System.out.println("age = " + age);
        }
        // Tuple은 패키지가  com.querydsl.core
        // Repository 계층 안에서 쓰는것은 괜찮으나 서비스계층이나 컨트롤러까지 넘어가는것은 좋은 설계가 아니다. 의존성 문제 야기할 수 잇음.
    }

    /**
     * 프로젝션과 결과 반환 DTO 조회
     * 순수 JPA를 사용해서 조회하는 방법과 QueryDsl을 사용해서 조회하는 방법이있다.
     * */
    // 1.JPQL을 사용해 조회
    @Test
    public void findDtoByJPQL(){
        //순수 JPA에서 DTO를 조회
        //단점
        // 1. 생성자방식만 지원한다. Setter, Getter를 사용하지 못한다.
        // 2. DTO의 package이름을 다 적어줘야해서 지저분함.

        List<MemberDto> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m", MemberDto.class).getResultList();
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    // 2. QueryDsl을 사용해서 조회 (프로퍼티 접근, 필드 집적 접근, 생성자 사용)
    @Test
    public void findDtoBySetter(){
         List<MemberDto> result = queryFactory
                 .select(Projections.bean(MemberDto.class,
                         member.username,
                         member.age))
                 .from(member)
                 .fetch();

         for (MemberDto memberDto : result) {
             System.out.println("memberDto = " + memberDto);
         }
     }

    @Test
    public void findDtoByField(){
        List<MemberDto> result = queryFactory
                //field는 필드명이 맞아야한다.
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findDtoByConstructor(){
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    //참고
    @Test
    public void findUserDtoByField(){
        QMember memberSub = new QMember("memberSub");

        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"),
                        ExpressionUtils.as(JPAExpressions
                                .select(memberSub.age.max())
                                    .from(memberSub), "age")
                ))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    @Test
    public void findUserDtoByConstructor(){
        List<UserDto> result = queryFactory
                //Constructor는 타입으로 구별하기 때문에 위의 코드와 동일하게 작성
                .select(Projections.constructor(UserDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    @Test
    public void findDtoByQueryProjection(){
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
        // Contructor 접근방식과의 차이점은 contructor 접근방법은 컴파일 오류를 잡아내지 못하는것에 비해, QueryProjection은 컴파일 오류까지 잡아낸다.
        // Dto 클래스 컨스트럭터에 @QueryProjection을 추가해주는 것으로 QMemberDto를 생성한다.
        // 문제점은.. Dto클래스에 QueryDsl에 의존성이 생긴다. >>> Dto의 순수성이 떨어짐.
    }

    /**
     * 동적쿼리 해결 방법
     * 1. BooleanBuilder
     * 2. Where 다중 파라미터 사용
     * */


    // 1.BooleanBuilder
    @Test
    public void dynamicQuery_BooleanBuilder(){
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {

        BooleanBuilder builder = new BooleanBuilder();
        if(usernameCond != null){
            builder.and(member.username.eq(usernameCond));
        }
        if(ageCond != null){
            builder.and(member.age.eq(ageCond));
        }
        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }


    // 2. where 다중 파라미터
    @Test
    public void dynamicQuery_WhereParam(){
        String usernameParam = "member1";
        Integer ageParam = null;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
                .where(usernameEq(usernameCond), ageEq(ageCond))
//                .where(allEq(usernameCond, ageCond))
                .fetch();
    }

    private BooleanExpression usernameEq(String usernameCond) {
        return usernameCond != null ? member.username.eq(usernameCond) : null;
    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    private BooleanExpression allEq(String usernameCond, Integer ageCond){
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }


    /**
     * 벌크연산(수정, 삭제)
     * */
    @Test
//    @Commit
    public void bulkUpdate(){

        //id : 1  member1 = 10 -> DB : member1
        //id : 2  member2 = 20 -> DB : member2
        //id : 3  member3 = 10 -> DB : member3
        //id : 4  member4 = 20 -> DB : member4

        //벌크연산은 영속성 컨텍스트를 무시하고 DB에 바로 쿼리가 나감 => DB의 상태와 영속성 컨텍스트의 상태가 달라져버림
        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(15))
                .execute();
        em.flush();
        em.clear();

        //id : 1  member1 = 10 -> DB : 비회원
        //id : 2  member2 = 10 -> DB : member2
        //id : 3  member3 = 10 -> DB : 비회원
        //id : 4  member4 = 20 -> DB : member4

        //영속성 컨텍스트가 우선권을 가지기 때문에 update 된 '비회원'이라는 username은 버려진다.
        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();
        for (Member member1 : result) {
            System.out.println("member1 = " + member1);
        }

        //이를 해결하기위해 벌크연산이 끝나고 영속성컨텍스트를 초기화를 해줘야한다.
    }

    @Test
    public void bulkAdd(){
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.add(2))
//                .set(member.age, member.age.multiply(2))
                .execute();
    }

    @Test
    public  void bulkDelete(){
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(15))
                .execute();
    }


    /**
     * SQL function 호출하기
     *      SQL function은 JPA와 같이 Dialect에 등록된 내용만 호출할 수 있다.
     * */
    @Test
    public void sqlFunction(){
        List<String> result = queryFactory
                .select(Expressions.stringTemplate(
                        "function('replace', {0}, {1}, {2})",
                        member.username, "member", "M"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }
    @Test
    public void sqlFunction2(){
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
//                .where(member.username.eq(Expressions.stringTemplate("fucntion('lower', {0})", member.username)))
                .where(member.username.eq(member.username.lower()))
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }

    }

}
