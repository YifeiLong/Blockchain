// SPDX-License-Identifier: GPL-3.0
pragma solidity >=0.4.16 <0.9.0;
pragma abicoder v2;

contract Adoption {
    // 宠物信息
    struct Pet {
        uint id;
        string name;
        uint price;
        address payable owner;
    }
    Pet[16] public pets;

    constructor() {
        for(uint i = 0; i < 16; i ++){
            pets[i] = Pet(i, concat("Pet", uint2str(i)), 0, payable(address(0)));
        }
    }

    // 领养宠物
    function adopt(uint petId) public payable {
        require(petId >= 0 && petId <= 15, "Pet ID must within valid range");
        require(msg.value >= pets[petId].price, "Sent ether must cover the price");
        pets[petId].owner.transfer(msg.value);
        pets[petId].owner = payable(msg.sender);
        pets[petId].price = 0;
    }

    function getAllPets() public view returns (Pet [16] memory) {
        return pets;
    }

    function uint2str(uint i) internal pure returns (string memory) {
        if(i == 0){
            return "0";
        }
        uint j = i;
        uint length;
        while(j != 0){
            length ++;
            j /= 10;
        }
        bytes memory bstr = new bytes(length);
        uint k = length;
        while(i != 0){
            k -= 1;
            bstr[k] = bytes1(uint8(48 + i % 10));
            i /= 10;
        }
        return string(bstr);
    }
    function concat(string memory a, string memory b) internal pure returns (string memory) {
        return string(abi.encodePacked(a, b));
    }
}
