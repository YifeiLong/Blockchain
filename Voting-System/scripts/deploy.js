const hre = require("hardhat");

async function main() {
    const [deployer] = await hre.ethers.getSigners(); // 获取所有签名者账户，并使用第一个账户部署合约

    console.log("Deploying contracts with the account:", deployer.address);
    console.log("Account balance:", (await deployer.getBalance()).toString());

    // 获取合约工厂
    const Contract = await hre.ethers.getContractFactory("Ballot");
    // 部署合约
    const contract = await Contract.deploy();

    console.log("Contract deployed to:", contract.address);
}

main()
    .then(() => process.exit(0))
    .catch(error => {
        console.error(error);
        process.exit(1);
    });
