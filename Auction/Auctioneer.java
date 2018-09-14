package CombineAuction;

import java.util.ArrayList;
import java.util.List;


import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.FSMBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class Auctioneer extends Agent {
	private static final double RESERVE_RATE = 0.4;
	private double initialPrice;
	private double reservePrice;
	
	
	protected void setup() {
		DFAgentDescription auctioneerDescription = new DFAgentDescription();
		auctioneerDescription.setName(getAID());
		
		ServiceDescription auctioneerService = new ServiceDescription();
		auctioneerService.setName("auctioneer");
		auctioneerService.setType("auctioneer");
		auctioneerDescription.addServices(auctioneerService);
		
		try {
			DFService.register(this, auctioneerDescription);
		} catch (FIPAException ex) {
			ex.printStackTrace();
		}
		
		getInitialPrice();
		addBehaviour(new AuctioneerBehaviour());
	}

	@Override
	protected void takeDown() {
		try {
			System.out.println("Terminating auctioneer [" + getAID().getLocalName() + "]...");
			DFService.deregister(this);
		} catch (FIPAException ex) {
			ex.printStackTrace();
		}
	}
	
	private void getInitialPrice() {
		Object args[] = getArguments();
		
		if (args != null && args.length >= 1) {
			try {
				initialPrice = Double.parseDouble(args[0].toString());
				setReservePrice();
			} catch (NumberFormatException ex) {
				System.out.println("Please type the initial price for the auction, in decimal form.");
				doDelete();
			}
		} else {
			System.out.println("Initial price not determined, terminating agent...");
			doDelete();
		}
	}
	
	private void setReservePrice() {
		reservePrice = initialPrice * RESERVE_RATE;
	}
	
	/*
	 * The agent will operate as a Finite State Machine (FSM).
	 * Each state is represented by a internal Behaviour.
	 */
	private class AuctioneerBehaviour extends FSMBehaviour {		
		// Constants for transition states
		private final int noWinner = 0;
		private final int hasWinner = 1;
		private final int validPrice = 2;
		private final int belowReserve = 3;
		private int exchangedInfo=0;
		
		private double currentPrice;
		private double reductionRate;
		private List<AID> buyers;
		private List<AID> EnglishParticipants;
		private List<AID> losers;
		private AID[] bidderAgent;
		
		public AuctioneerBehaviour() {
			currentPrice = initialPrice;
			reductionRate = (initialPrice - reservePrice) * 0.1;
			buyers = new ArrayList<>();
			EnglishParticipants = new ArrayList<>();
			losers = new ArrayList<>();
			
			registerFirstState(new SearchBuyersBehaviour(), "searching for buyers");
			registerState(new InformBuyersBehaviour(), "inform buyers");
			registerState(new CallBuyersBehaviour(), "call for proposal");
			registerState(new ReceiveProposalsBehaviour(), "receiving proposals");
			registerState(new AcceptProposalBehaviour(), "accepting proposal");
			registerState(new ReducePriceBehaviour(), "reducing price");
			registerState(new EndingAuctionBehaviour(), "ending auction");
			registerState(new EnglishCall(), "english call");
			registerState(new FindWinner(),"find winner");
			registerLastState(new EndingAuction(),"final");
			
			
			
			
			registerDefaultTransition("searching for buyers", "inform buyers");
			registerDefaultTransition("inform buyers", "call for proposal");
			registerDefaultTransition("call for proposal", "receiving proposals");
			registerTransition("receiving proposals", "reducing price", noWinner);
			registerTransition("receiving proposals", "accepting proposal", hasWinner);
			registerTransition("reducing price", "call for proposal", validPrice);
			registerTransition("reducing price", "ending auction", belowReserve);
			registerDefaultTransition("accepting proposal", "ending auction");
			registerDefaultTransition("ending auction","english call");
			
			registerDefaultTransition("english call","find winner");
			registerTransition("find winner", "english call", noWinner);
			registerTransition("find winner", "final", hasWinner);


			
		}
		
		/*
		 * Looks for existing buyers so the auction can start.
		 */
		private class SearchBuyersBehaviour extends OneShotBehaviour {
			
			@Override
			public void action() {
				
				DFAgentDescription template = new DFAgentDescription();
				ServiceDescription serviceTemplate = new ServiceDescription();
				serviceTemplate.setType("buyer");
				template.addServices(serviceTemplate);
				
				try {
					DFAgentDescription[] result = DFService.search(myAgent, template);
					if (result.length == 0) {
						System.out.println("No buyers found...");
					} else {
						System.out.println("Buyers found:");
						for (int i = 0; i < result.length; i++) {
							buyers.add(result[i].getName());
							System.out.println(buyers.get(i).getName());
						}
					}
				} catch (FIPAException ex) {
					ex.printStackTrace();
				}
			}		
		}
		
		/*
		 * Informs existing buyers that the auction is about to begin.
		 */
		private class InformBuyersBehaviour extends OneShotBehaviour {
			
			
			
			@Override
			public void action() {
				exchangedInfo++;
				ACLMessage message = new ACLMessage(ACLMessage.INFORM);
				message.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
				message.setContent("begin-auction");
				
				for (AID buyer : buyers) {
					message.addReceiver(buyer);
				}
				myAgent.send(message);
				
				System.out.println("Auctioneer: The auction is about to begin...");
			}
		}
		
		/*
		 * Sends a call for proposal (CFP) to existing buyers.
		 */
		private class CallBuyersBehaviour extends OneShotBehaviour {
			
			@Override
			public void action() {
				exchangedInfo++;
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				cfp.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
				cfp.setContent(Double.toString(currentPrice));
				
				for (AID buyer : buyers) {
					cfp.addReceiver(buyer);
					System.out.println("Auctioneer: Sending CFP to [" + buyer.getLocalName() +"]");
				}
				myAgent.send(cfp);
			}
		}
		
		/*
		 * Waits for response to the CFP from all buyers.
		 * Having a winner proceeds to accept the proposal;
		 * Otherwise proceeds to reducing the value.
		 */
		private class ReceiveProposalsBehaviour extends Behaviour {
			private int transitionStatus = noWinner;
			private int responsesReceived = 0;
			
			private AID highestBidder = null;
			private int highestBid=0;
			
			
			@Override
			public void action() {
				exchangedInfo++;
				MessageTemplate proposeTemplate = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
				MessageTemplate refuseTemplate = MessageTemplate.MatchPerformative(ACLMessage.REFUSE);
				MessageTemplate messageTemplate = MessageTemplate.or(proposeTemplate, refuseTemplate);
				ACLMessage cfpResponse = myAgent.receive(messageTemplate);
				
				if (cfpResponse != null) {
					// First response wins the auction
					if (cfpResponse.getPerformative() == ACLMessage.PROPOSE) {
						
							EnglishParticipants.add (cfpResponse.getSender());
							transitionStatus = hasWinner;}
						 else {
							losers.add(cfpResponse.getSender());
						}
					
					responsesReceived++;
					System.out.println("Buyer [" + cfpResponse.getSender().getName() + "] answered : My decision for this round is "
							+ ACLMessage.getPerformative(cfpResponse.getPerformative()));
				} else { 
					block();
				}
			}

			@Override
			public boolean done() {
				return responsesReceived == buyers.size();
			}

			@Override
			public int onEnd() {
				// Resetting number of responses in case the behaviour is executed again
				responsesReceived = 0;
				return transitionStatus;
			}
		}
		
		/*
		 * Informs the EnglishParticipants of the auction and rejects all other proposals.
		 */
		private class AcceptProposalBehaviour extends OneShotBehaviour {
			
			public void action() {
				exchangedInfo++;
				// Inform the winner
				ACLMessage acceptMessage = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				acceptMessage.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
				for(AID winner:EnglishParticipants){
				acceptMessage.addReceiver(winner);
				}
				myAgent.send(acceptMessage);
				
				
				// Inform the ones who lost due to late response
				ACLMessage rejectMessage = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
				rejectMessage.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
				for (AID loser : losers) {
					rejectMessage.addReceiver(loser);
				}
				myAgent.send(rejectMessage);
			}
		}
		
		/*
		 * If there were no proposals, reduce price and send new CFP.
		 * If the new price hits the reserve value, end auction without EnglishParticipants.
		 */
		private class ReducePriceBehaviour extends OneShotBehaviour {			
			private int transitionStatus = validPrice;
			
			@Override
			public void action() {
				exchangedInfo++;
				currentPrice -= reductionRate;
				System.out.println("Auctioneer: No bids this round. Lowering price to: " + currentPrice);
				System.out.println("Auctioneer: Reserve price is " + reservePrice);
				
				if (currentPrice < reservePrice) {
					transitionStatus = belowReserve;
					System.out.println("Auctioneer: Hit reserve value! Ending auction...");
				}
			}

			@Override
			public int onEnd() {
				return transitionStatus;
			}
		}
		
		/*
		 * Ends the DUTCH auction 
		 */
		private class EndingAuctionBehaviour extends OneShotBehaviour {
			@Override
			public void action() {
				ACLMessage informMessage = new ACLMessage(ACLMessage.INFORM);
				informMessage.setProtocol(FIPANames.InteractionProtocol.FIPA_DUTCH_AUCTION);
				for (AID buyer : buyers) {
					informMessage.addReceiver(buyer);
				}
				myAgent.send(informMessage);
				
				if (EnglishParticipants != null) {
					for (AID EngP:EnglishParticipants) {
							
							System.out.println("Auctioneer: Dutch Auction finished and " + EngP.getName() + 
							" will continue for English Auction!");
							
						}
					
				} else {
					System.out.println("Auction finished without EnglishParticipants..");
				}
				
				
			}		
		}
		
		/*
		 * Start the English auction 
		 */
		private class EnglishCall extends OneShotBehaviour {
			@Override
			public void action() {
				exchangedInfo++;
				ACLMessage EngCall = new ACLMessage(ACLMessage.CFP);
				EngCall.setProtocol(FIPANames.InteractionProtocol.FIPA_ENGLISH_AUCTION);
				EngCall.setContent(Double.toString(currentPrice));
				
				for (AID EngP : EnglishParticipants) {
					EngCall.addReceiver(EngP);
					System.out.println("Auctioneer: Sending English Auction CFP to ["+EngP.getLocalName()+"]");
				}
				
				
				myAgent.send(EngCall);
				
				
				
				
			}		
		}
		
		
		
		/*
		 * Findwinner
		 * 
		 */
		private class FindWinner extends Behaviour {			
			private int transitionStatus=noWinner;
			
			
			private int resonsesReceived=0;
			
			@Override
			public void action() {
				exchangedInfo++;
				System.out.println("Auctioneer: Start collecting English Bids");
				MessageTemplate proposeTemplate = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE);
				MessageTemplate refuseTemplate = MessageTemplate.MatchPerformative(ACLMessage.REFUSE);
				MessageTemplate messageTemplate = MessageTemplate.or(proposeTemplate, refuseTemplate);
				ACLMessage cfpResponse = myAgent.receive(messageTemplate);
				
				if (cfpResponse != null) {
					
					
					// First response wins the auction
					if (cfpResponse.getPerformative() == ACLMessage.PROPOSE) {
							currentPrice=Integer.parseInt(cfpResponse.getContent());
							System.out.println("Auctioneer: I  received bid from "+cfpResponse.getSender().getName()+" as "+currentPrice);
							currentPrice=1.1*currentPrice;
							System.out.println("Auctioneer(Thinking in head): I will increase the price to "+currentPrice);
							
							

					} else if(cfpResponse.getPerformative() == ACLMessage.REFUSE) { 
					System.out.println("Auctioneer: no Proposal recived, the winner will be the one who bid the highest offer last turn");
					transitionStatus=hasWinner;
					} 
					resonsesReceived++;
				}else{block();}
				
				
			}
			@Override
			public boolean done(){return resonsesReceived==EnglishParticipants.size();}
			@Override
			public int onEnd(){resonsesReceived=0; return transitionStatus;}

			
		}
		
		/*
		 * Ends the English auction 
		 */
		private class EndingAuction extends OneShotBehaviour {
			@Override
			public void action() {
				
					
			System.out.println("Auctioneer: Auction End");
			System.out.println("=================================================================");
			System.out.println("||The nummber of messages exchanged is: "+exchangedInfo);					
			System.out.println("||The profit gained is: "+(currentPrice-reservePrice));	
			System.out.println("=================================================================");			
			}		
		}	
	}
}
