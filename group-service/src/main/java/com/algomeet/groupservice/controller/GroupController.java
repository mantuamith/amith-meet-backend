package com.algomeet.groupservice.controller;

import com.algomeet.groupservice.model.Group;
import com.algomeet.groupservice.repository.GroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/groups")
public class GroupController {

    @Autowired
    private GroupRepository groupRepository;

    @PostMapping("/create")
    public ResponseEntity<?> createGroup(@RequestBody Group group, Principal principal) {
        group.getMembers().add(principal.getName());
        return ResponseEntity.ok(groupRepository.save(group));
    }

    @PostMapping("/{groupId}/join")
    public ResponseEntity<?> joinGroup(@PathVariable String groupId, Principal principal) {
        Optional<Group> groupOpt = groupRepository.findById(Long.valueOf(groupId));
        if (groupOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Group not found");
        }

        Group group = groupOpt.get();
        String userId = principal.getName();

        if (group.getMembers().contains(userId)) {
            return ResponseEntity.status(409).body("User already a member of the group");
        }

        group.getMembers().add(userId);
        return ResponseEntity.ok(groupRepository.save(group));
    }

    @GetMapping("/{groupName}")
    public ResponseEntity<?> getGroup(@PathVariable String groupName) {
        return groupRepository.findByName(groupName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{groupId}/leave")
    public ResponseEntity<?> leaveGroup(@PathVariable String groupId, Principal principal) {
        Optional<Group> groupOpt = groupRepository.findById(Long.valueOf(groupId));
        if (groupOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Group not found");
        }

        Group group = groupOpt.get();
        String userId = principal.getName();

        if (!group.getMembers().contains(userId)) {
            return ResponseEntity.status(404).body("User is not a member of the group");
        }

        group.getMembers().remove(userId);
        groupRepository.save(group);

        return ResponseEntity.ok("User removed from group");
    }

    @GetMapping("/groups/mine")
    public ResponseEntity<List<Group>> getMyGroups(Principal principal) {
        List<Group> myGroups = groupRepository.findByMembersContaining(principal.getName());
        return ResponseEntity.ok(myGroups);
    }
}
