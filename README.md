![Logo](logo-h.png)


# BrokerChain Wallet

## AI Agent Prediction Market Extension

The `agent/gold` module adds a gold-note prediction market to BrokerChain Wallet. It provides market browsing, YES/NO AMM trading, portfolio valuation, DeepSeek research, AI-managed positions, and settlement status display.

New manual and AI-created markets share one versioned template catalog:

- `TYPE_PRICE`: XAU/USD direction over a committed day window.
- `TYPE_RETURN_THRESHOLD`: absolute XAU/USD return against a percentage threshold.
- `TYPE_PRICE_THRESHOLD`: deadline XAU/USD price against a USD/oz threshold.
- `TYPE_PRICE_RANGE`: deadline XAU/USD price inside or outside a closed range.
- `TYPE_RELATIVE`: XAU/USD return compared with BTC/USD return.
- `TYPE_STREAK`: consecutive daily XAU/USD increases or decreases.

Every new rule uses Beijing midnight boundaries, a 1-4 day observation window, and the public Ethereum Chainlink XAU/USD feed. Relative markets also commit the Chainlink BTC/USD feed. Sunday and Monday boundaries are moved together with the complete window so its duration remains unchanged. Older market types remain displayable but are not available for new creation.

### Local prerequisites

`AgentConfig.LOCAL_HOST` defaults to `10.0.2.2`, the Android emulator alias for the host machine. Before testing the complete flow, start the PredictionMarket backend on host port `8081`, the configured local EVM/BrokerChain endpoint, MySQL, and the selected IPFS service. A physical Android device must replace `10.0.2.2` with a reachable host LAN address.

The app requests gold quotes and research through the backend first, with its existing direct-source fallbacks. DeepSeek research and AI market parsing require either a working backend AI provider or a valid API Key configured in the AI research tab. Invalid credentials, DNS failures, quota exhaustion, or HTTP 402 are shown as unavailable states; they are not caused by the local Supervisor wallet key.

For a safe local build without starting trading services:

```bash
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
./gradlew testDebugUnitTest assembleDebug
```


## Basics

### BrokerChain Wallet
BrokerChain Wallet is the native wallet for **BrokerChain** (https://github.com/HuangLab-SYSU/brokerchain-academic).
Using BrokerChain Wallet, users can submit token-transfer transactions, receive payments, stake tokens to DeFi protocols, become brokers (i.e., market makers) to earn liquidity staking revenue, receive faucet tokens, participate in airdrops, and so on.


### Four typical BrokerChain Participants

In BrokerChain’s ecosystem, everyone can become a researcher, miner, user (such as staker, market maker, etc), or dApp developer.

![Roles](img/img_15.png)

- **Researchers** can use this project's open-source code to implement technological innovations at the bottom of the chain.
- **Miners** can join the Testnet to earn mining rewards. Please visit the release page ( https://github.com/HuangLab-SYSU/brokerchain-academic/releases/ ) to download the miner client and join the network.
- **Developers** can build dApps by deploying smart contracts to the embedded EVM. Please review the README.md to learn how to deploy/invoke smart contracts.
- **Users** can initiate token-transfer transactions, invest in BrokerChain on-chain financial products, and participate in other activities held by dApps. 


### Follow our X Media
Please follow our X (Twitter) account: https://x.com/0xBrokerChain. We share BrokerChain news in this X account.



## Download BrokerChain Wallet

Download the compiled **APK** file from the BrokerChain Wallet's Github repository Release page (https://github.com/HuangLab-SYSU/brokerwallet-academic/releases) and send it to your phone for installation. Currently, BrokerChain Wallet only supports Android devices. The iOS version will be released soon.


![test](img/img_5.png)



## Import an Account

After entering the main page of BrokerChain Wallet, click on the upper right corner to enter the menu list, click on the Settings option to enter the settings page, and click on the Accounts option to enter the account management page.


![test1](img/img_6.png)


After entering the account management page, click the Add account button and enter the account's private key to import it into the Account private key input box. (<span style="color: red;">**Note that you should get the private key from the BrokerChain miner client, do not fill a key randomly, otherwise the wallet may fail to import account**</span>)

Click the Save button to save this account. The account list will display the address and balance of the account that was just imported. Click on the account, select it, and it will be used as the payer account in the future.


![test2](img/img_7.png)



## Initiate a Token-transfer Transaction

Enter the main page of BrokerChain Wallet. Click the Send button to enter the transfer page. Enter the recipient's account address in the Send to input box, the transfer amount (in BKC) in the Amount input box, and the transfer fee (in **BKC**) in the Fee input box. Click the Send button to initiate a transfer transaction. After the transfer is successful, return to the main page of BrokerChain Wallet, and you will see that the transfer amount and transaction fee have been deducted from the account balance. The transfer amount will be credited to the recipient's account, and the transfer fee will be used as the broker's income.


![test3](img/img_8.png)



## Use the Faucet Function of BrokerChain Wallet to Receive Airdropped Coins

BrokerChain Wallet provides a **Faucet** function, allowing users to receive a small amount of test coins for free. Enter the main page of BrokerChain Wallet, click the Faucet button, and enter the faucet page. Click the Claim button to receive some BKC coins. Users can only receive once per account per day. However, each IP address can claim only a few faucet tokens.

![test4](img/img_9.png)


## Disclaimers

- BrokerChain (academic) is only for educational purposes, and users are not allowed to engage in any illegal activities using BrokerChain (academic).
- Any direct or indirect consequences arising from users' use of BrokerChain are unrelated to the founding team of BrokerChain.
- The founding team of BrokerChain (academic) reserves the right to modify, update, or terminate BrokerChain (academic) without prior notice to users.
- When using BrokerChain (academic), users should bear the risks themselves and agree to waive any claims against the founding team.
- This disclaimer is governed by and interpreted according to the laws of the People's Republic of China.
