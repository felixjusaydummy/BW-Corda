package net.corda.koalanab.blockwallet.states;

import net.corda.koalanab.blockwallet.contracts.LoanContract;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Getter
@AllArgsConstructor
@BelongsToContract( LoanContract.class)
public class LoanState implements ContractState {

    private final UniqueIdentifier linearId;

    private final long walletAccountId;
    private final String purpose;
    private final String amount;
    private final String paymentTerms;
    private final String occupation;
    private final String grossIncome;
    private final UniqueIdentifier kycId;
    private final boolean approved;

//    private final LocalDateTime dateApproved;
//    private final LocalDateTime dateRequested;
//    private final LocalDateTime dateRejected;

    private final String dateApproved;
    private final String dateRequested;
    private final String dateRejected;

    private final String remarks;
    private final String creditScore;
    private final String paidRemarks;
    private final String datePaid;

    private Party sender;
    private Party receiver;

    @Override
    public List<AbstractParty> getParticipants() { return Arrays.asList(sender,receiver); }
}


