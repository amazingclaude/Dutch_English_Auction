package CombineAuction;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class Bidder extends Agent {
	
	private double priceToBuy; //the lowest price for bidder
	private double highpriceToBuy; //the higherst price for bidder
	final double d = Math.random();
	final int i = (int)(d*5)+1;
	
	protected void setup() {
		DFAgentDescription buyerDescription = new DFAgentDescription();
		buyerDescription.setName(getAID());
		
		ServiceDescription buyerService = new ServiceDescription();
		buyerService.setName("buyer");
		buyerService.setType("buyer");
		buyerDescription.addServices(buyerService);
		
		try {
			DFService.register(this, buyerDescription);
		} catch (FIPAException ex) {
			ex.printStackTrace();
		}
		
		getPriceToBuy();
		addBehaviour(new BuyerBehaviour());
	}

	@Override
	protected void takeDown() {
		try {
			System.out.println("Terminating buyer [" + getAID().getLocalName() + "]...");
			DFService.deregister(this);
		} catch (FIPAException ex) {
			ex.printStackTrace();
		}
	}
	
	private void getPriceToBuy() {
		Object args[] = getArguments();
		
		if (args != null && args.length >= 1) {
			try {
				priceToBuy = Double.parseDouble(args[0].toString());
				
				highpriceToBuy=priceToBuy*i;
				
			} catch (NumberFormatException ex) {
				System.out.println("Please type the interested price for the auction, in decimal form.");
				doDelete();
			}
		} else {
			System.out.println("Buying price not determined, terminating agent...");
			doDelete();
		}
	}
	
	/*
	 * The agent will operate as a Finite State Machine (FSM).
	 * Each state is represented by a internal Behaviour.
	 */
	private class BuyerBehaviour extends FSMBehaviour {
		
		// Constants for transition states 
		private final int noBuyers = 0;
		private final int sendProposal = 1;
		private final int highPrice = 2;
		private final int lowPrice = 3;
		
		public BuyerBehaviour() {
			registerFirstState(new WaitAuctionBehaviour(), "waiting for auction");
			registerState(new ReceiveCfpBehaviour(), "waiting for cfp");
			registerState(new ReceiveReplyBehaviour(), "waiting for reply");
			registerState(new ReceiveEnglishCfp(),"receiving english cfp");
			registerLastState(new AuctionFinishedBehaviour(), "ending auction");
			
			
			registerDefaultTransition("waiting for auction", "waiting for cfp");
			registerTransition("waiting for cfp", "ending auction", noBuyers);
			registerTransition("waiting for cfp", "waiting for reply", sendProposal); 
			registerTransition("waiting for cfp", "waiting for cfp", highPrice);
			registerDefaultTransition("waiting for reply", "receiving english cfp");
			registerTransition("receiving english cfp","receiving english cfp",lowPrice);
			registerTransition("receiving english cfp", "ending auction", noBuyers);
		}
		
		/*
		 * Waits for an inform from an auctioneer that the auction is about to start.
		 */
		private class WaitAuctionBehaviour extends Behaviour {
			
			private boolean stopWaiting = false;

			@Override
			public void action() {
				MessageTemplate messageTemplate = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
				ACLMessage inform = myAgent.receive(messageTemplate);
				
				if (inform != null) {
					System.out.println("Buyer [" + getAID().getLocalName() + "] was informed.");
					stopWaiting = true;
				} else {
					block();
				}
			}

			@Override
			public boolean done() {
				return stopWaiting;
			}
		}
		
		/*
		 * Waits for a CFP and replies accordingly depending on the price given.
		 */
		private class ReceiveCfpBehaviour extends Behaviour {			
			private boolean hasReceivedMessage = false;
			private int transitionStatus;

			@Override
			public void action() {
				/*
				 * There are two possible types of message to be received:
				 * A CFP, indicating the auctioneer is still trying to sell;
				 * An inform, indicating that the auction has ended.
				 */
				MessageTemplate cfpTemplate = MessageTemplate.MatchPerformative(ACLMessage.CFP);
				MessageTemplate informTemplate = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
				MessageTemplate messageTemplate = MessageTemplate.or(cfpTemplate, informTemplate);
				ACLMessage receivedMessage = myAgent.receive(messageTemplate);
				
				if (receivedMessage != null) {
					hasReceivedMessage = true;
					
					if (receivedMessage.getPerformative() == ACLMessage.CFP) {
						double price = Double.parseDouble(receivedMessage.getContent());
						System.out.println("Buyer [" + getAID().getLocalName() + "] received "
								+ "CFP with price " + Double.toString(price));
						
						// Value in range; send proposal
						if (price <= priceToBuy) {
							ACLMessage proposal = new ACLMessage(ACLMessage.PROPOSE);
							proposal.addReceiver(receivedMessage.getSender());
							proposal.setProtocol(receivedMessage.getProtocol());
							myAgent.send(proposal);
							
							transitionStatus = sendProposal;
						} else {
							// Price too high; refuse and wait for new CFP
							ACLMessage refusal = new ACLMessage(ACLMessage.REFUSE);
							refusal.addReceiver(receivedMessage.getSender());
							refusal.setProtocol(receivedMessage.getProtocol());
							myAgent.send(refusal);
							
							transitionStatus = highPrice;
						}
					} else if (receivedMessage.getPerformative() == ACLMessage.INFORM) {
						transitionStatus = noBuyers;
					}
				} else {
					block();
				}
			}

			@Override
			public boolean done() {
				return hasReceivedMessage;
			}
			
			@Override
			public int onEnd() {
				return transitionStatus;
			}
		}
		
		/*
		 * Waits for an answer from the auctioneer about the proposal made.
		 */
		private class ReceiveReplyBehaviour extends Behaviour {
			
			private boolean receivedReply = false;
			private MessageTemplate acceptTemplate = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			private MessageTemplate rejectTemplate = MessageTemplate.MatchPerformative(ACLMessage.REJECT_PROPOSAL);
			private MessageTemplate messageTemplate = MessageTemplate.or(acceptTemplate, rejectTemplate);
			
			@Override
			public void action() {
				ACLMessage replyMessage = myAgent.receive(messageTemplate);
				if (replyMessage != null) {
					receivedReply = true;
					switch(replyMessage.getPerformative()) {
						case ACLMessage.ACCEPT_PROPOSAL:
							System.out.println(myAgent.getLocalName() + ": I will continue the  Auction!");
							
							break;
						case ACLMessage.REJECT_PROPOSAL:
							System.out.println(myAgent.getLocalName() + " didn't make it on time... "
									+ "better luck in the next one!");
							break;
					}
				}
			}
			
			@Override
			public boolean done() {
				return receivedReply;
			}
		}
			
		
		/*
		 * Receive English Cfp.
		 */
		private class ReceiveEnglishCfp extends Behaviour {
			private boolean receivedCfp=false;
			private int transitionStatus;
			int bid;
			
			@Override
			public void action() {
				MessageTemplate cfpTemplate = MessageTemplate.MatchPerformative(ACLMessage.CFP);
				
				ACLMessage EnglishCfp = myAgent.receive(cfpTemplate);
				
				
				
				if (EnglishCfp!=null){
					
					receivedCfp=true;

					double englishPrice=Double.parseDouble(EnglishCfp.getContent());
					System.out.println(getAID().getLocalName()+": I received the English CFP as "+ Double.toString(englishPrice));
					if(englishPrice<highpriceToBuy){
						bid=(int)(englishPrice*1.1);
						ACLMessage Engproposal=new ACLMessage(ACLMessage.PROPOSE);
						
						System.out.println(getAID().getLocalName()+": I will bid as "+ Double.toString(bid));
						
						Engproposal.addReceiver(EnglishCfp.getSender());
						Engproposal.setProtocol(EnglishCfp.getProtocol());
						Engproposal.setContent(String.valueOf(bid));
						myAgent.send(Engproposal);
						transitionStatus=lowPrice;
					}else{
						ACLMessage Engrefusal=new ACLMessage(ACLMessage.REFUSE);
						
						System.out.println(getAID().getLocalName()+": It's too high I will not bid");
						
						Engrefusal.addReceiver(EnglishCfp.getSender());
						Engrefusal.setProtocol(EnglishCfp.getProtocol());
						myAgent.send(Engrefusal);
						transitionStatus=noBuyers;
						doDelete();
					}
					
				}else{block();}		
			}
			
			@Override
				public boolean done(){return receivedCfp;}
			@Override
				public int onEnd(){return transitionStatus;}
			
		}
		
		
		/*
		 * Takes down the agent after the auction has finished.
		 */
		private class AuctionFinishedBehaviour extends OneShotBehaviour {

			@Override
			public void action() {
				myAgent.doDelete();
			}
		}
		
		
		
		
		
	}
}
