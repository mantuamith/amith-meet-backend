package com.algomeet.groupservice.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.groupservice.controller.swagger.InternalGroupControllerDoc;
import com.algomeet.groupservice.dto.GroupResponse;
import com.algomeet.groupservice.service.GroupService;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@RestController
@RequestMapping("/internal/groups")
public class InternalGroupController implements InternalGroupControllerDoc{
	private final GroupService groupService;

	@GetMapping("/{groupId}")
	public GroupResponse getGroup(@PathVariable Long groupId) {
		return groupService.getGroupById(groupId);
	}

	@GetMapping("/member/username/{username}")
	public List<GroupResponse> getGroupsForUsername(@PathVariable String username) {
		return groupService.getGroupsByUsername(username);
	}
}
