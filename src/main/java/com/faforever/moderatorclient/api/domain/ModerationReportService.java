package com.faforever.moderatorclient.api.domain;


import com.faforever.commons.api.dto.ModerationReport;
import com.faforever.commons.api.dto.ModerationReportStatus;
import com.faforever.commons.api.elide.ElideNavigator;
import com.faforever.moderatorclient.api.FafApiCommunicationService;
import com.faforever.moderatorclient.mapstruct.ModerationReportMapper;
import com.faforever.moderatorclient.ui.domain.ModerationReportFX;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ModerationReportService {

	private final ModerationReportMapper moderationReportMapper;
	private final FafApiCommunicationService fafApi;

	public ModerationReportService(ModerationReportMapper moderationReportMapper, FafApiCommunicationService fafApi) {
		this.moderationReportMapper = moderationReportMapper;
		this.fafApi = fafApi;
	}

	public void patchReport(ModerationReport dto) {
		fafApi.patch(ElideNavigator.of(ModerationReport.class).id(dto.getId()), dto);
	}

	public CompletableFuture<List<ModerationReportFX>> getPageOfReports(int page, int pageSize) {
		return CompletableFuture.supplyAsync(() -> {
            List<ModerationReport> reports = fafApi.getPage(ModerationReport.class, ElideNavigator.of(ModerationReport.class)
					.collection()
					.addInclude("reporter")
                    .addInclude("reporter.bans")
					.addInclude("game")
					.addInclude("lastModerator")
					.addInclude("reportedUsers")
                    .addInclude("reportedUsers.bans")
					.addSortingRule("id", false), pageSize, page,
					ImmutableMap.of()

			);

			return reports.stream().map(moderationReportMapper::map).collect(Collectors.toList());
		});
	}
}

