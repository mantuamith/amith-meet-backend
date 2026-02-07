package com.algomeet.groupservice.controller;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.groupservice.controller.swagger.InternalGroupControllerDoc;
import com.algomeet.groupservice.mapper.GroupMapper;
import com.algomeet.groupservice.model.Group;
import com.algomeet.groupservice.repository.GroupRepository;

@RestController
@RequestMapping("/internal/groups")
public class InternalGroupController implements InternalGroupControllerDoc{
    @Autowired
    private GroupRepository groupRepository;

    @GetMapping("/{groupId}")
    public Object getGroup(@PathVariable Long groupId) {    	
        Optional<Group> group = groupRepository.findById(groupId);
        
        if(group.isPresent()) {
        	return GroupMapper.toResponse(group.get());
        } else {
        	return ResponseEntity.notFound().build(); 
        }
    }    
}
