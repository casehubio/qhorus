package io.casehub.qhorus.persistence.memory.contract;

import io.casehub.qhorus.api.channel.Space;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

abstract class SpaceStoreContractTest {

    protected abstract Space put(Space space);

    protected abstract Optional<Space> find(UUID id);

    protected abstract List<Space> findByName(String name);

    protected abstract List<Space> listByParent(UUID parentSpaceId);

    protected abstract List<Space> listRoots();

    protected abstract boolean hasChildren(UUID spaceId);

    protected abstract void delete(UUID id);

    private static final String TENANCY = "default";

    private Space rootSpace() {
        return new Space(UUID.randomUUID(), "project-alpha", "Alpha project space",
                         null, TENANCY, Instant.now());
    }

    private Space childSpace(UUID parentId) {
        return new Space(UUID.randomUUID(), "case-123", "Case 123 space",
                         parentId, TENANCY, Instant.now());
    }

    @Test
    void putAndFind() {
        Space space = rootSpace();
        Space saved = put(space);
        assertThat(saved.id()).isEqualTo(space.id());
        assertThat(saved.name()).isEqualTo("project-alpha");

        Optional<Space> found = find(space.id());
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("project-alpha");
        assertThat(found.get().parentSpaceId()).isNull();
    }

    @Test
    void find_nonExistent_returnsEmpty() {
        assertThat(find(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByName_returnsMatches() {
        put(rootSpace());
        assertThat(findByName("project-alpha")).hasSize(1);
        assertThat(findByName("nonexistent")).isEmpty();
    }

    @Test
    void listRoots_returnsOnlyRootSpaces() {
        Space root1 = put(rootSpace());
        Space root2 = put(new Space(UUID.randomUUID(), "project-beta", null,
                                    null, TENANCY, Instant.now()));
        put(childSpace(root1.id()));

        List<Space> roots = listRoots();
        assertThat(roots).extracting(Space::id)
                         .containsExactlyInAnyOrder(root1.id(), root2.id());
    }

    @Test
    void listByParent_returnsDirectChildren() {
        Space root   = put(rootSpace());
        Space child1 = put(childSpace(root.id()));
        Space child2 = put(new Space(UUID.randomUUID(), "case-456", null,
                                     root.id(), TENANCY, Instant.now()));
        put(new Space(UUID.randomUUID(), "sub-task", null,
                      child1.id(), TENANCY, Instant.now()));

        List<Space> children = listByParent(root.id());
        assertThat(children).extracting(Space::id)
                            .containsExactlyInAnyOrder(child1.id(), child2.id());
    }

    @Test
    void listByParent_nonExistentParent_returnsEmpty() {
        assertThat(listByParent(UUID.randomUUID())).isEmpty();
    }

    @Test
    void hasChildren_trueWhenChildrenExist() {
        Space root = put(rootSpace());
        put(childSpace(root.id()));
        assertThat(hasChildren(root.id())).isTrue();
    }

    @Test
    void hasChildren_falseWhenNoChildren() {
        Space root = put(rootSpace());
        assertThat(hasChildren(root.id())).isFalse();
    }

    @Test
    void hasChildren_falseForNonExistentSpace() {
        assertThat(hasChildren(UUID.randomUUID())).isFalse();
    }

    @Test
    void delete_removesSpace() {
        Space space = put(rootSpace());
        delete(space.id());
        assertThat(find(space.id())).isEmpty();
    }

    @Test
    void delete_nonExistent_noOp() {
        delete(UUID.randomUUID());
    }

    @Test
    void recursiveNesting_threeLevelsDeep() {
        Space org = put(new Space(UUID.randomUUID(), "org-acme", "Organization",
                                  null, TENANCY, Instant.now()));
        Space team = put(new Space(UUID.randomUUID(), "team-eng", "Engineering",
                                   org.id(), TENANCY, Instant.now()));
        Space project = put(new Space(UUID.randomUUID(), "proj-x", "Project X",
                                      team.id(), TENANCY, Instant.now()));

        assertThat(listRoots()).extracting(Space::id).containsExactly(org.id());
        assertThat(listByParent(org.id())).extracting(Space::id).containsExactly(team.id());
        assertThat(listByParent(team.id())).extracting(Space::id).containsExactly(project.id());
        assertThat(listByParent(project.id())).isEmpty();
    }
}
