package com.algomeet.groupservice.mapper;

import com.algomeet.groupservice.dto.GroupRequest;
import com.algomeet.groupservice.dto.GroupResponse;
import com.algomeet.groupservice.dto.MemberRequest;
import com.algomeet.groupservice.dto.MemberResponse;
import com.algomeet.groupservice.model.Group;
import com.algomeet.groupservice.model.Member;

import java.util.HashSet;
import java.util.Set;

public class GroupMapper {

	private GroupMapper() {
		// utility class
	}

	public static Group toEntity(GroupRequest req) {
		if (req == null) {
			return null;
		}

		Group group = new Group();
		group.setName(req.getName());

		Set<Member> members = new HashSet<>();
		if (req.getMembers() != null) {
			for (MemberRequest memberReq : req.getMembers()) {
				Member member = new Member(memberReq.getUserKey(), memberReq.getUsername());
				members.add(member);
			}
		}

		group.setMembers(members);
		return group;
	}

	/*
	 * ========================= Entity → Response =========================
	 */
	public static GroupResponse toResponse(Group entity) {
		if (entity == null) {
			return null;
		}

		GroupResponse dto = new GroupResponse();
		dto.setId(entity.getId());
		dto.setName(entity.getName());
		dto.setOwnerUserKey(entity.getOwnerUserKey());

		Set<MemberResponse> members = new HashSet<>();
		if (entity.getMembers() != null) {
			for (Member member : entity.getMembers()) {
				MemberResponse memberResp = new MemberResponse();
				memberResp.setUserKey(member.getUserKey());
				memberResp.setUsername(member.getUsername());
				memberResp.setRole(member.getRole());
				members.add(memberResp);
			}
		}

		dto.setMembers(members);
		return dto;
	}
}
