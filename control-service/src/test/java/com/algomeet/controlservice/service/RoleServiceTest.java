package com.algomeet.controlservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.algomeet.controlservice.dto.RoleRequest;
import com.algomeet.controlservice.dto.RoleResponse;
import com.algomeet.controlservice.entity.Role;
import com.algomeet.controlservice.exception.RecordNotFoundException;
import com.algomeet.controlservice.exception.RoleIdAlreadyExistsException;
import com.algomeet.controlservice.exception.RoleNameAlreadyExistsException;
import com.algomeet.controlservice.repository.RoleRepository;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private RoleService roleService;

    private RoleRequest request;
    private Role role;

    @BeforeEach
    void setUp() {
        request = new RoleRequest();
        request.setId("admin");
        request.setName("Administrator");
        request.setDescription("Admin role");

        role = new Role();
        role.setId("ADMIN");
        role.setName("Administrator");
        role.setDescription("Admin role");
    }

    /* -------------------------------------------------
     * CREATE ROLE
     * ------------------------------------------------- */

    @Test
    void createRole_success() {
        when(roleRepository.existsById("ADMIN")).thenReturn(false);
        when(roleRepository.existsByNameIgnoreCase("Administrator")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenReturn(role);

        RoleResponse response = roleService.createRole(request);

        assertNotNull(response);
        assertEquals("ADMIN", response.getId());
        assertEquals("Administrator", response.getName());

        verify(roleRepository).save(any(Role.class));
    }

    @Test
    void createRole_idAlreadyExists() {
        when(roleRepository.existsById("ADMIN")).thenReturn(true);

        assertThrows(
            RoleIdAlreadyExistsException.class,
            () -> roleService.createRole(request)
        );

        verify(roleRepository, never()).save(any());
    }

    @Test
    void createRole_nameAlreadyExists() {
        when(roleRepository.existsById("ADMIN")).thenReturn(false);
        when(roleRepository.existsByNameIgnoreCase("Administrator")).thenReturn(true);

        assertThrows(
            RoleNameAlreadyExistsException.class,
            () -> roleService.createRole(request)
        );

        verify(roleRepository, never()).save(any());
    }

    /* -------------------------------------------------
     * GET ROLE
     * ------------------------------------------------- */

    @Test
    void getRole_success() {
        when(roleRepository.findById("ADMIN"))
            .thenReturn(Optional.of(role));

        RoleResponse response = roleService.getRole("ADMIN");

        assertEquals("ADMIN", response.getId());
        assertEquals("Administrator", response.getName());
    }

    @Test
    void getRole_notFound() {
        when(roleRepository.findById("ADMIN"))
            .thenReturn(Optional.empty());

        assertThrows(
            RecordNotFoundException.class,
            () -> roleService.getRole("ADMIN")
        );
    }

    /* -------------------------------------------------
     * GET ALL ROLES
     * ------------------------------------------------- */

    @Test
    void getAllRoles_success() {
        Role role2 = new Role();
        role2.setId("USER");
        role2.setName("User");

        when(roleRepository.findAll())
            .thenReturn(List.of(role, role2));

        List<RoleResponse> responses = roleService.getAllRoles();

        assertEquals(2, responses.size());
        assertEquals("ADMIN", responses.get(0).getId());
        assertEquals("USER", responses.get(1).getId());
    }

    /* -------------------------------------------------
     * UPDATE ROLE
     * ------------------------------------------------- */

    @Test
    void updateRole_success() {
        when(roleRepository.findById("ADMIN"))
            .thenReturn(Optional.of(role));
        when(roleRepository.save(any(Role.class)))
            .thenReturn(role);

        request.setName("Updated Name");
        request.setDescription("Updated Desc");

        RoleResponse response = roleService.updateRole("ADMIN", request);

        assertEquals("Updated Name", response.getName());
        assertEquals("Updated Desc", response.getDescription());
    }

    @Test
    void updateRole_notFound() {
        when(roleRepository.findById("ADMIN"))
            .thenReturn(Optional.empty());

        assertThrows(
            RecordNotFoundException.class,
            () -> roleService.updateRole("ADMIN", request)
        );
    }

    /* -------------------------------------------------
     * DELETE ROLE
     * ------------------------------------------------- */

    @Test
    void deleteRole_success() {
        when(roleRepository.existsById("ADMIN"))
            .thenReturn(true);

        roleService.deleteRole("ADMIN");

        verify(roleRepository).deleteById("ADMIN");
    }

    @Test
    void deleteRole_notFound() {
        when(roleRepository.existsById("ADMIN"))
            .thenReturn(false);

        assertThrows(
            RecordNotFoundException.class,
            () -> roleService.deleteRole("ADMIN")
        );

        verify(roleRepository, never()).deleteById(any());
    }
}
