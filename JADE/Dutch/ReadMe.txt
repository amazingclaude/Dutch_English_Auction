Instruction of running the code:

1. Open CMD, and go to the directory where the "Dutch", "English" and  "jade" file exist.

2. Compile the java file using:

javac -classpath jade\lib\jade.jar -d Dutch Dutch\*.java

3. Running the software using:

java -cp jade\lib\jade.jar;Dutch jade.Boot -agents Seller:Dutch.Auctioneer(500);Bidder1:Dutch.Bidder(400);Bidder2:Dutch.Bidder(400)
