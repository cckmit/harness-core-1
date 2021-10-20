package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.UserJourneyDTO;
import io.harness.cvng.servicelevelobjective.beans.UserJourneyResponse;
import io.harness.cvng.servicelevelobjective.entities.UserJourney;
import io.harness.cvng.servicelevelobjective.entities.UserJourney.UserJourneyKeys;
import io.harness.cvng.servicelevelobjective.services.UserJourneyService;
import io.harness.ng.beans.PageResponse;
import io.harness.persistence.HPersistence;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import org.mongodb.morphia.query.Sort;

public class UserJourneyServiceImpl implements UserJourneyService {
  @Inject private HPersistence hPersistence;

  @Override
  public UserJourneyResponse create(ProjectParams projectParams, UserJourneyDTO userJourneyDTO) {
    saveUserJourneyEntity(projectParams.getAccountIdentifier(), userJourneyDTO);
    return getUserJourneyResponse(userJourneyDTO.getIdentifier(), projectParams);
  }

  private UserJourneyResponse getUserJourneyResponse(String identifier, ProjectParams projectParams) {
    UserJourney userJourney = hPersistence.createQuery(UserJourney.class)
                                  .filter(UserJourneyKeys.accountId, projectParams.getAccountIdentifier())
                                  .filter(UserJourneyKeys.orgIdentifier, projectParams.getOrgIdentifier())
                                  .filter(UserJourneyKeys.projectIdentifier, projectParams.getProjectIdentifier())
                                  .filter(UserJourneyKeys.identifier, identifier)
                                  .get();

    return userJourneyEntityToUserJourneyResponse(userJourney);
  }

  private UserJourneyResponse userJourneyEntityToUserJourneyResponse(UserJourney userJourney) {
    UserJourneyDTO userJourneyDTO = UserJourneyDTO.builder()
                                        .orgIdentifier(userJourney.getOrgIdentifier())
                                        .projectIdentifier(userJourney.getProjectIdentifier())
                                        .identifier(userJourney.getIdentifier())
                                        .name(userJourney.getName())
                                        .build();
    return UserJourneyResponse.builder()
        .userJourneyDTO(userJourneyDTO)
        .createdAt(userJourney.getCreatedAt())
        .lastModifiedAt(userJourney.getLastUpdatedAt())
        .build();
  }

  private void saveUserJourneyEntity(String accountId, UserJourneyDTO userJourneyDTO) {
    UserJourney userJourney = UserJourney.builder()
                                  .accountId(accountId)
                                  .projectIdentifier(userJourneyDTO.getProjectIdentifier())
                                  .orgIdentifier(userJourneyDTO.getOrgIdentifier())
                                  .name(userJourneyDTO.getName())
                                  .identifier(userJourneyDTO.getIdentifier())
                                  .build();
    hPersistence.save(userJourney);
  }

  @Override
  public PageResponse<UserJourneyResponse> getUserJourneys(
      ProjectParams projectParams, Integer offset, Integer pageSize) {
    List<UserJourney> userJourneyList =
        hPersistence.createQuery(UserJourney.class)
            .filter(UserJourneyKeys.accountId, projectParams.getAccountIdentifier())
            .filter(UserJourneyKeys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(UserJourneyKeys.projectIdentifier, projectParams.getProjectIdentifier())
            .order(Sort.descending(UserJourneyKeys.lastUpdatedAt))
            .asList();

    PageResponse<UserJourney> userJourneyEntityPageResponse =
        PageUtils.offsetAndLimit(userJourneyList, offset, pageSize);
    List<UserJourneyResponse> userJourneyPageResponses = userJourneyEntityPageResponse.getContent()
                                                             .stream()
                                                             .map(this::userJourneyEntityToUserJourneyResponse)
                                                             .collect(Collectors.toList());

    return PageResponse.<UserJourneyResponse>builder()
        .pageSize(pageSize)
        .pageIndex(offset)
        .totalPages(userJourneyEntityPageResponse.getTotalPages())
        .totalItems(userJourneyEntityPageResponse.getTotalItems())
        .pageItemCount(userJourneyEntityPageResponse.getPageItemCount())
        .content(userJourneyPageResponses)
        .build();
  }
}
