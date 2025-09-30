package com.algomeet.controlservice.service;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.controlservice.dto.RoleRequest;
import com.algomeet.controlservice.dto.RoleResponse;
import com.algomeet.controlservice.entity.Role;
import com.algomeet.controlservice.exception.RecordNotFoundException;
import com.algomeet.controlservice.exception.RoleIdAlreadyExistsException;
import com.algomeet.controlservice.exception.RoleNameAlreadyExistsException;
import com.algomeet.controlservice.repository.RoleRepository;

import java.util.List;

@Service
@Transactional
public class RoleService {
    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public RoleResponse createRole(RoleRequest request) {
    	if (roleRepository.existsById(request.getId().toUpperCase())) {
            throw new RoleIdAlreadyExistsException("Role Id already exists");
        }
    	
        if (roleRepository.existsByNameIgnoreCase(request.getName())) {
            throw new RoleNameAlreadyExistsException("Role name already exists");
        }
        
        Role role = new Role();
        role.setId(request.getId().toUpperCase());
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        Role saved = roleRepository.save(role);
        return mapToResponse(saved);
    }

    public RoleResponse getRole(String id) {
        return roleRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RecordNotFoundException("Role not found"));
    }

    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public RoleResponse updateRole(String id, RoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RecordNotFoundException("Role not found"));

        role.setName(request.getName());
        role.setDescription(request.getDescription());
        Role updated = roleRepository.save(role);
        return mapToResponse(updated);
    }

    public void deleteRole(String id) {
        if (!roleRepository.existsById(id)) {
            throw new RecordNotFoundException("Role not found");
        }
        roleRepository.deleteById(id);
    }

    private RoleResponse mapToResponse(Role role) {
        RoleResponse response = new RoleResponse();
        response.setId(role.getId());
        response.setName(role.getName());
        response.setDescription(role.getDescription());
        response.setCreatedAt(role.getCreatedAt());
        response.setModifiedAt(role.getModifiedAt());
        return response;
    }
}