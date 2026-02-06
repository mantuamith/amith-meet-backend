package com.algomeet.groupservice.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.groupservice.dto.GroupDto;
import com.algomeet.groupservice.dto.MemberDto;
import com.algomeet.groupservice.mapper.GroupMapper;
import com.algomeet.groupservice.model.Group;
import com.algomeet.groupservice.model.Member;
import com.algomeet.groupservice.repository.GroupRepository;
import com.algomeet.groupservice.util.SecurityUtil;

@RestController
@RequestMapping("/groups")
public class GroupController {
    @Autowired
    private GroupRepository groupRepository;

    @PostMapping("/create")
    public ResponseEntity<?> createGroup(@RequestBody GroupDto groupDto, Authentication authentication) {    	
    	MemberDto member = new MemberDto();
    	member.setUsername(authentication.getName());
    	member.setUserKey(SecurityUtil.getUserKey());
    	
    	groupDto.getMembers().add(member);
        
        Group group = GroupMapper.toEntity(groupDto);        
        return ResponseEntity.ok(groupRepository.save(group));
    }

    @PostMapping("/{groupId}/join")
    public ResponseEntity<?> joinGroup(@PathVariable String groupId, Authentication authentication) {
        Optional<Group> groupOpt = groupRepository.findById(Long.valueOf(groupId));
        if (groupOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Group not found");
        }

        Group group = groupOpt.get();
        Member member = new Member();

        member.setUsername(authentication.getName());
        member.setUserKey(SecurityUtil.getUserKey());
        
        if (group.getMembers().parallelStream()
        		.anyMatch(m -> m.getUserKey().equals(member.getUserKey()))) {
            return ResponseEntity.status(409).body("User already a member of the group");
        }

        group.getMembers().add(member);
        return ResponseEntity.ok(groupRepository.save(group));
    }

    @GetMapping("/{groupName}")
    public ResponseEntity<?> getGroup(@PathVariable String groupName) {
        return groupRepository.findByName(groupName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{groupId}/leave")
    public ResponseEntity<?> leaveGroup(@PathVariable String groupId, Authentication authentication) {
        Optional<Group> groupOpt = groupRepository.findById(Long.valueOf(groupId));
        if (groupOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Group not found");
        }

        Group group = groupOpt.get();
        String username = authentication.getName();
        String userKey = SecurityUtil.getUserKey();
        Member removeMember = new Member(userKey, username);
        
        if (!group.getMembers().contains(removeMember)) {
            return ResponseEntity.status(404).body("User is not a member of the group");
        }

        group.getMembers().remove(removeMember);
        groupRepository.save(group);

        return ResponseEntity.ok("User removed from group");
    }

    @GetMapping("/groups/mine")
    public ResponseEntity<List<GroupDto>> getMyGroups(Authentication authentication) {
        List<Group> myGroups = groupRepository.findByMembersContaining(authentication.getName());
        
        List<GroupDto> myGroupDtos = new ArrayList<>();
        if (!CollectionUtils.isEmpty(myGroups)) {
        	myGroups.forEach(g -> {
        		myGroupDtos.add(GroupMapper.toDto(g));
        	});
        }
        
        return ResponseEntity.ok(myGroupDtos);
    }
}
