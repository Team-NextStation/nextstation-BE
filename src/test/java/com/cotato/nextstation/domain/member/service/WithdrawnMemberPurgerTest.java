package com.cotato.nextstation.domain.member.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class WithdrawnMemberPurgerTest {

    @InjectMocks
    private WithdrawnMemberPurger withdrawnMemberPurger;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @BeforeEach
    void setUpQuery() {
        given(entityManager.createNativeQuery(anyString())).willReturn(query);
        given(query.setParameter(eq("ids"), any())).willReturn(query);
    }

    private List<String> executedSql() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        then(entityManager).should(org.mockito.Mockito.atLeastOnce()).createNativeQuery(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("파기 대상 회원의 행과 관련 데이터를 자식 → 부모 순서로 삭제한다")
    void purge_hardDeletesMemberAndRelatedRows() {
        // when
        withdrawnMemberPurger.purge(List.of(1L, 2L));

        // then
        List<String> sqls = executedSql();
        // member 행 삭제는 반드시 마지막 - 앞선 문장들이 member_id로 자식을 찾기 때문
        assertThat(sqls.get(sqls.size() - 1)).isEqualTo("DELETE FROM member WHERE id IN (:ids)");
        // 자식이 부모보다 먼저
        assertThat(indexOfTable(sqls, "place_review_image")).isLessThan(indexOfTable(sqls, "place_review"));
        assertThat(indexOfTable(sqls, "place_review")).isLessThan(indexOfTable(sqls, "journal"));
        assertThat(indexOfTable(sqls, "course_places")).isLessThan(indexOfTable(sqls, "course"));
        // Apple/카카오 revoke 재료(social_oauth_credential)도 member_social_account보다 먼저 지운다
        assertThat(indexOfTable(sqls, "social_oauth_credential")).isLessThan(indexOfTable(sqls, "member_social_account"));
        // 회원이 남긴 흔적이 어느 테이블에도 남지 않는다
        assertThat(sqls).allMatch(sql -> sql.startsWith("DELETE FROM"));
        assertThat(tables(sqls)).contains("journal", "journal_image", "course", "course_like", "place_review",
                "place_review_like", "member_place_stamps", "member_terms_agreement", "social_oauth_credential",
                "member_social_account", "email_verification", "recommendation_log", "member");
        then(query).should(org.mockito.Mockito.atLeastOnce()).setParameter("ids", List.of(1L, 2L));
    }

    private List<String> tables(List<String> sqls) {
        return sqls.stream().map(sql -> sql.split(" ")[2]).toList();
    }

    private int indexOfTable(List<String> sqls, String table) {
        return tables(sqls).indexOf(table);
    }
}
