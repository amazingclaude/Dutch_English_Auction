package English;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.concurrent.ThreadLocalRandom;

public class Bidder extends Agent {
    private int wallet;

    @Override
    protected void setup() {

        setRandomWallet();

        addBehaviour(new BidRequestsServer());

        // Register the auction-seller service in the yellow pages
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("auction-bidder");
        sd.setName("MultiAgentSystem-auctions");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        System.out.println(getAID().getName() + ": I am ready. (Thinking in head: My HighestPrice is $" + wallet/2+")");
    }

    private void setRandomWallet() {
        int min = 2000;
        int max = 10000;

        wallet = ThreadLocalRandom.current().nextInt(min, max);
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        System.out.println("Bidder " + getAID().getName() + " terminating");
    }

    private class BidRequestsServer extends Behaviour {
        private String itemName;
        private Integer itemPrice;

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive();

            if (msg != null) {
                parseContent(msg.getContent());

                ACLMessage reply = msg.createReply();
                int bid;

                if (itemPrice < wallet / 2) {
                    // Place a bid 5 to 10% higher than the received value
                    bid = (int) (itemPrice + itemPrice * ((float) ThreadLocalRandom.current().nextInt(5, 10) / 10));

                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(String.valueOf(bid));
                } else {
                    reply.setPerformative(ACLMessage.REFUSE);
                }

                myAgent.send(reply);
            } else {
                block();
            }
        }

        private void parseContent(String content) {
            String[] split = content.split("\\|\\|");

            itemName = split[0];
            itemPrice = Integer.parseInt(split[1]);
        }

        @Override
        public boolean done() {
            return false;
        }
    }
}
