package English;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


public class Auctioneer extends Agent {
    private AID[] bidderAgents;

    private String itemName;
    private Integer itemPrice;

    @Override
    protected void setup() {       

        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            
            itemPrice = Integer.parseInt((String) args[0]);

            System.out.println("Auctioneer: The English Auction will start with the starting price of " + itemPrice);

            addBehaviour(new OneShotBehaviour() {
                @Override
                public void action() {
                    // Get all the BidderAgents
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("auction-bidder");
                    template.addServices(sd);

                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);

                        bidderAgents = new AID[result.length];
                        for (int i = 0; i < result.length; i++) {
                            //System.out.println("Found seller: " + result[i].getName());

                            bidderAgents[i] = result[i].getName();
                        }
                    } catch (FIPAException e) {
                        e.printStackTrace();
                    }

                    myAgent.addBehaviour(new AuctionPerformer());
                }
            });
        } else {
            System.out.println("No item to be auctioned this time");
            doDelete();
        }
    }

    private class AuctionPerformer extends Behaviour {
        private int step = 0;
        private Map<AID, Integer> receivedProposals = new HashMap<>();
        private int numExpectedProposals = 0;

        private MessageTemplate mt;
        private AID highestBidder = null;
        private int highestBid = 0;
		private int updatePrice = 0;

        private int roundsWithNoOffers = 0;

        @Override
        public void action() {
            switch (step) {
                case 0:
                    // Reinitialize the expected proposals
                    receivedProposals = new HashMap<>();
                    numExpectedProposals = 0;

                    // Send the item being sold and the starting bidding price
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);

                    for (int i = 0; i < bidderAgents.length; i++) {
                        if (highestBidder == null || (highestBidder != null && bidderAgents[i].compareTo(highestBidder) != 0)) {
                            cfp.addReceiver(bidderAgents[i]);

                            numExpectedProposals++;
                        }
                    }

                    if (highestBidder != null) {
                        cfp.setContent(itemName + "||" + highestBid);
                    } else {
                        cfp.setContent(itemName + "||" + itemPrice);
                    }

                    cfp.setConversationId("auction");
                    cfp.setReplyWith("cfp" + System.currentTimeMillis());

                    myAgent.send(cfp);

                    // Prepare the message template to deal with the bidding proposals
                    mt = MessageTemplate.and(
                            MessageTemplate.MatchConversationId("auction"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));

                    step = 1;
                    break;
                case 1:
                    ACLMessage reply = myAgent.receive(mt);

                    if (reply != null) {

                        switch (reply.getPerformative()) {
                            case ACLMessage.PROPOSE:
                                // This is a bid
                                receivedProposals.put(reply.getSender(), Integer.parseInt(reply.getContent()));

                                System.out.println(reply.getSender().getName() + ": I bid " + reply.getContent());

                                // Reinitialize if there are offers
                                roundsWithNoOffers = 0;

                                break;
                            case ACLMessage.REFUSE:
                                // The agent is not interested in the item
                                receivedProposals.put(reply.getSender(), null);

                                // Increment the amount of rounds with no offers
                                roundsWithNoOffers++;
                                break;
                        }

                        if (receivedProposals.size() == numExpectedProposals) {
                            step = 2;
                        }

                    } else {
                        block();
                    }
                    break;
                case 2:

                    // Checks bids and save the highest one
                    Iterator<Map.Entry<AID, Integer>> iter = receivedProposals.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<AID, Integer> item = iter.next();
                        if (item.getValue() != null && highestBid < item.getValue()) {
                            highestBidder = item.getKey();
                            highestBid = item.getValue();
                        }
                    }

                    if (highestBidder != null) {
                        System.out.println("Auctioneer: Highest bid so far: " + highestBid + " for " + highestBidder.getName());
                    } else {
                        System.out.println("Only received invalid bids!");
                    }

                    // Send accept proposal to the highest bidder

                    ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    accept.addReceiver(highestBidder);
                    accept.setContent(itemName + "||" + highestBid);
                    accept.setConversationId("auction");
                    accept.setReplyWith("bid-ok" + System.currentTimeMillis());

                    myAgent.send(accept);

                    // Reject the rest of the proposals

                    receivedProposals.keySet().stream()
                            .filter(aid -> aid != highestBidder && receivedProposals.get(aid) != null)
                            .forEach(aid -> {
                                ACLMessage reject = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                                reject.addReceiver(highestBidder);
                                reject.setContent(itemName + "||" + receivedProposals.get(aid));
                                reject.setConversationId("auction");
                                reject.setReplyWith("bid-reject" + System.currentTimeMillis());

                                myAgent.send(reject);
                            });

                    step = 3;
                    break;
                case 3:
					
                    System.out.println("Auctioneer: Do I hear $" + String.valueOf(highestBid * 1.1) + "?");

                    if (roundsWithNoOffers != 0) {
                        System.out.println("Auctioneer: The higherst Bid "+highestBid + " for " + roundsWithNoOffers+" time");
                    }

                    if (roundsWithNoOffers == 3) {
                        step = 4;
                    } else {
                        step = 0;
                    }
                    break;
                case 4:

                    System.out.println("Auctioneer: Sold to the bidder " + highestBidder.getName() + " for $" + highestBid);
                    step = 5;
                    break;
            }
        }

        @Override
        public boolean done() {
            return (step == 5);
        }
    }
}
