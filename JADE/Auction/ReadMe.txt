Instruction of running the code:

1. Open CMD, and go to the directory where the "Auction" and  "jade" file exist.

2. Compile the java file using:

javac -classpath jade\lib\jade.jar -d Auction Auction\*.java

3. Running the software using:

java -cp jade\lib\jade.jar;Auction jade.Boot -agents Seller:CombineAuction.Auctioneer(500);Bidder1:CombineAuction.Bidder(400);Bidder2:CombineAuction.Bidder(400)
