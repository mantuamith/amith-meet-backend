package com.algomeet.mediaservice.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.mediaservice.controller.swagger.InternalFileControllerDoc;
import com.algomeet.mediaservice.service.UserFileService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/media")
@RequiredArgsConstructor
public class InternalFileController implements InternalFileControllerDoc {
	private final UserFileService userFileService;

	@PostMapping("/{mediaId}/share")
	public void share(@PathVariable String mediaId, @RequestParam String userKey, @RequestParam List<String> shareWithUserKeys) {
		userFileService.shareFile(mediaId, userKey, shareWithUserKeys);
	}

	@DeleteMapping("/{mediaId}")
	public void delete(@PathVariable String mediaId,
			@RequestParam String userKey,
			@RequestParam(required = false) List<String> deleteWithUserKeys) {

		userFileService.softDeleteAndMarkForCleanupIfOrphaned(mediaId, userKey,
				deleteWithUserKeys);
	}
}
