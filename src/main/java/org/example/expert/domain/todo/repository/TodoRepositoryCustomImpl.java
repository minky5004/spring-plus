package org.example.expert.domain.todo.repository;

import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.expert.domain.todo.dto.response.TodoSearchResponse;
import org.example.expert.domain.todo.entity.Todo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.example.expert.domain.comment.entity.QComment.comment;
import static org.example.expert.domain.manager.entity.QManager.manager;
import static org.example.expert.domain.todo.entity.QTodo.todo;
import static org.example.expert.domain.user.entity.QUser.user;

@Repository
@RequiredArgsConstructor
public class TodoRepositoryCustomImpl implements TodoRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Todo> findByIdWithUser(Long todoId) {
        return Optional.ofNullable(
                queryFactory
                        .selectFrom(todo)
                        .leftJoin(todo.user, user).fetchJoin()
                        .where(todo.id.eq(todoId))
                        .fetchOne()
        );
    }

    @Override
    public Page<TodoSearchResponse> searchTodos(
            String title,
            LocalDateTime startDate,
            LocalDateTime endDate,
            String nickname,
            Pageable pageable) {

        List<TodoSearchResponse> content = queryFactory
                .select(Projections.constructor(TodoSearchResponse.class,
                        todo.title,

                        ExpressionUtils.as(
                                JPAExpressions.select(manager.count())
                                        .from(manager)
                                        .where(manager.todo.eq(todo)),
                                "managerCount"
                        ),

                        ExpressionUtils.as(
                                JPAExpressions.select(comment.count())
                                        .from(comment)
                                        .where(comment.todo.eq(todo)),
                                "commentCount"
                        )
                ))
                .from(todo)
                .where(
                        titleContains(title),
                        createdDateBetween(startDate, endDate),
                        nicknameContains(nickname)
                )
                .orderBy(todo.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long totalCount = queryFactory
                .select(todo.count())
                .from(todo)
                .where(
                        titleContains(title),
                        createdDateBetween(startDate, endDate),
                        nicknameContains(nickname)
                )
                .fetchOne();

        long total = (totalCount != null) ? totalCount : 0L;

        return new PageImpl<>(content, pageable, total);
    }


    private BooleanExpression titleContains(String title) {
        return title != null ? todo.title.contains(title) : null;
    }

    private BooleanExpression createdDateBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null && end == null) return null;
        if (start == null) return todo.createdAt.loe(end);
        if (end == null) return todo.createdAt.goe(start);
        return todo.createdAt.between(start, end);
    }

    private BooleanExpression nicknameContains(String nickname) {
        if (nickname == null) return null;

        return todo.id.in(
                JPAExpressions.select(manager.todo.id)
                        .from(manager)
                        .join(manager.user, user)
                        .where(user.nickname.contains(nickname))
        );
    }
}