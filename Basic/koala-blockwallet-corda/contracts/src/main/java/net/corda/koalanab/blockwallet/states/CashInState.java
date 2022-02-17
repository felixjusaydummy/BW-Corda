package net.corda.koalanab.blockwallet.states;

import net.corda.koalanab.blockwallet.contracts.CashInContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;

import java.util.Arrays;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@BelongsToContract( CashInContract.class)
public class CashInState implements ContractState {

    private final UniqueIdentifier linearId;
    private final String affiliateAccount;
    private final String walletAccount;
    private final String amount;

    private final Party sender;
    private final Party receiver;

    @Override
    public List<AbstractParty> getParticipants() { return Arrays.asList(sender,receiver); }
}


