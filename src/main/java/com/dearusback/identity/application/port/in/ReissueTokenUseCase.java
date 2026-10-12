package com.dearusback.identity.application.port.in;

import com.dearusback.identity.application.model.ReissueTokenCommand;
import com.dearusback.identity.application.model.ReissueTokenResult;

public interface ReissueTokenUseCase {

    ReissueTokenResult reissue(ReissueTokenCommand command);
}
