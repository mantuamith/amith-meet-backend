package com.algomeet.groupservice.mapper;

import com.algomeet.groupservice.dto.GroupDto;
import com.algomeet.groupservice.dto.MemberDto;
import com.algomeet.groupservice.model.Group;
import com.algomeet.groupservice.model.Member;

import java.util.HashSet;
import java.util.Set;

public class GroupMapper {

	private GroupMapper() {
		// utility class
	}

	public static Group toEntity(GroupDto dto) {
		if (dto == null) {
			return null;
		}

		Group group = new Group();
		group.setId(dto.getId());
		group.setName(dto.getName());

		Set<Member> members = new HashSet<>();
		if (dto.getMembers() != null) {
			for (MemberDto memberDto : dto.getMembers()) {
				Member member = new Member();
				member.setUserKey(memberDto.getUserKey());
				member.setUsername(memberDto.getUsername());
				members.add(member);
			}
		}

		group.setMembers(members);
		return group;
	}

	/*
	 * ========================= Entity → DTO =========================
	 */
	public static GroupDto toDto(Group entity) {
		if (entity == null) {
			return null;
		}

		GroupDto dto = new GroupDto();
		dto.setId(entity.getId());
		dto.setName(entity.getName());

		Set<MemberDto> members = new HashSet<>();
		if (entity.getMembers() != null) {
			for (Member member : entity.getMembers()) {
				MemberDto memberDto = new MemberDto();
				memberDto.setUserKey(member.getUserKey());
				memberDto.setUsername(member.getUsername());
				members.add(memberDto);
			}
		}

		dto.setMembers(members);
		return dto;
	}
}
