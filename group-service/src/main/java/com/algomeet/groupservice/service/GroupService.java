package com.algomeet.groupservice.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.algomeet.groupservice.dto.AddGroupMembersRequest;
import com.algomeet.groupservice.dto.GroupRequest;
import com.algomeet.groupservice.dto.GroupResponse;
import com.algomeet.groupservice.dto.MemberRequest;
import com.algomeet.groupservice.dto.UpdateGroupRequest;
import com.algomeet.groupservice.enums.ResponseCode;
import com.algomeet.groupservice.exceptions.GroupNotFoundException;
import com.algomeet.groupservice.mapper.GroupMapper;
import com.algomeet.groupservice.model.Group;
import com.algomeet.groupservice.model.Member;
import com.algomeet.groupservice.repository.GroupRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;

    public GroupResponse createGroup(GroupRequest request, String username, String userKey) {

        MemberRequest creator = new MemberRequest();
        creator.setUsername(username);
        creator.setUserKey(userKey);

        request.getMembers().add(creator);

        Group group = GroupMapper.toEntity(request);
        return GroupMapper.toResponse(groupRepository.save(group));
    }

    public void removeGroup(Long groupId) throws GroupNotFoundException {
        Group group = getGroupOrThrow(groupId);
        groupRepository.delete(group);
    }

    public GroupResponse joinGroup(Long groupId, String username, String userKey) throws GroupNotFoundException {
        Group group = getGroupOrThrow(groupId);

        Member member = new Member(userKey, username);
        if (group.getMembers().contains(member)) {
            throw new IllegalStateException(ResponseCode.USER_ALREADY_GROUP_MEMBER.name());
        }

        group.getMembers().add(member);
        return GroupMapper.toResponse(groupRepository.save(group));
    }

    public GroupResponse addGroupMembers(Long groupId, AddGroupMembersRequest request) throws GroupNotFoundException {
        Group group = getGroupOrThrow(groupId);

        Set<Member> newMembers = request.getMembers().stream()
                .map(m -> new Member(m.getUserKey(), m.getUsername()))
                .filter(m -> !group.getMembers().contains(m))
                .collect(Collectors.toSet());

        if (newMembers.isEmpty()) {
            throw new IllegalStateException(ResponseCode.USER_ALREADY_GROUP_MEMBER.name());
        }

        group.getMembers().addAll(newMembers);
        return GroupMapper.toResponse(groupRepository.save(group));
    }

    public void leaveGroup(Long groupId, String userKey) throws GroupNotFoundException {
        Group group = getGroupOrThrow(groupId);

        Member member = new Member(userKey, null);
        if (!group.getMembers().remove(member)) {
            throw new IllegalStateException(ResponseCode.GROUP_MEMBER_NOT_FOUND.name());
        }

        groupRepository.save(group);
    }

    public void removeGroupMember(Long groupId, String userKey) throws GroupNotFoundException {
        leaveGroup(groupId, userKey);
    }

    public List<GroupResponse> getMyGroups(String username) {
        List<Group> groups = groupRepository.findByMembersContaining(username);

        if (CollectionUtils.isEmpty(groups)) {
            return List.of();
        }

        return groups.stream()
                .map(GroupMapper::toResponse)
                .collect(Collectors.toList());
    }

    private Group getGroupOrThrow(Long groupId) throws GroupNotFoundException {
        return groupRepository.findById(groupId)
                .orElseThrow(() ->
                        new GroupNotFoundException("Group Id not found"));
    }
    
    public GroupResponse getGroupById(Long groupId) {
        Group group = getGroupOrThrow(groupId);

        return GroupMapper.toResponse(group);
    }
    
    public GroupResponse updateGroup(Long groupId, UpdateGroupRequest request) {
    	Group group = getGroupOrThrow(groupId);
    	
    	if (StringUtils.isNotBlank(request.getName())) {
    		group.setName(request.getName());
    	}
    	
    	return GroupMapper.toResponse(groupRepository.save(group));    	    	
    }     
}
