Instruction of running the code:

1. Open CMD, and go to the directory where the "Dutch", "English" and  "jade" file exist.

2. Compile the java file using:

javac -classpath jade\lib\jade.jar -d English English\*.java

3. Running the software using:

java -cp jade\lib\jade.jar;English jade.Boot -agents Seller:English.Auctioneer(500);Bidder1:English.Bidder;Bidder2:English.Bidder

