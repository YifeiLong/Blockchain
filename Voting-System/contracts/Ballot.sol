// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.0;

contract Ballot{
    struct Poll {
        string title;  // 投票标题
        uint256 endTime;  // 投票结束时间
        uint256 reward;  // 投票奖励
        uint256 joinPrice;  // 参加投票费用
        address creator;  // 投票创建者地址
        mapping(address => bool) participants;  // 参与投票者地址与状态映射
        mapping(address => bool) isVoted;  // 参与投票者地址与投票状态映射
        mapping(uint => uint) voteNum;  // 选项与投票数映射
        uint totalVote;  // 总投票数
        string[] options;  // 投票选项
        bool isValid;  // 投票是否有效
        address[] winner;  // 获胜者地址数组
    }

    mapping(uint => Poll) public polls;  // 投票编号与投票的映射
    uint public pollCount;  // 当前投票个数

    event PollCreated(uint pollId, string title, uint256 endTime, uint256 reward,
        uint256 joinPrice, string[] options, address creator);
    event PollJoined(uint pollId, address participant);
    event PollVoted(uint pollId, address voter, uint256 optionId);
    event PollClosed(uint pollId, address[] winners);

    // 创建新投票
    function createPoll(string memory _title, uint256 _endTime, uint256 _reward, uint256 _joinPrice,
        string[] memory _options) external payable {
        // 保证支付金额足够
        require(msg.value >= _reward, "Fund insufficient");

        // 投票选项不能重复
        for(uint i = 0; i < _options.length; i ++){
            for(uint j = i + 1; j < _options.length; j ++){
                require(keccak256(abi.encodePacked(_options[i])) != keccak256(abi.encodePacked(_options[j])), "Duplicated Options");
            }
        }

        // 初始化投票信息
        uint pollId = pollCount ++;
        Poll storage poll = polls[pollId];
        poll.title = _title;
        poll.endTime = _endTime;
        poll.reward = _reward;
        poll.creator = msg.sender;
        poll.options = _options;
        poll.isValid = true;
        poll.joinPrice = _joinPrice;

        emit PollCreated(pollId, _title, _endTime, _reward, _joinPrice, _options, msg.sender);
    }

    // 获取当前所有正在进行中的投票
    function getAllPolls() public view returns (uint[] memory, string[] memory, string[][] memory) {
        uint activeCount = 0;  // 当前还在进行中投票数
        uint[] memory activeIndex = new uint[](pollCount);

        for(uint i = 0; i < pollCount; i ++){
            if (polls[i].isValid && block.timestamp < polls[i].endTime) {
                activeIndex[activeCount] = i;
                activeCount++;
            }
        }

        uint[] memory ids = new uint[](activeCount);
        string[] memory titles = new string[](activeCount);
        string[][] memory optionsList = new string[][](activeCount);

        for (uint i = 0; i < activeCount; i++) {
            uint pollId = activeIndex[i];
            Poll storage poll = polls[pollId];
            ids[i] = pollId;
            titles[i] = poll.title;
            optionsList[i] = poll.options;
        }

        return (ids, titles, optionsList);
    }

    // 成为投票选项
    function joinPoll(uint _pollId) external payable {
        Poll storage poll = polls[_pollId];  // 当前投票
        // 检查参加条件
        require(poll.isValid, "Poll is invalid");
        require(!poll.participants[msg.sender], "Already joined before");
        require(msg.value >= poll.joinPrice, "Insufficient fund for joining the poll");

        // 检查是否已经成为选项
        for(uint i = 0; i < poll.options.length; i ++){
            require(keccak256(abi.encodePacked(poll.options[i])) != keccak256(abi.encodePacked(toAsciiString(msg.sender))),
                "Address already an option");
        }

        address creator = poll.creator;
        // 支付金额给投票发起者
        payable(creator).transfer(msg.value);
        // 将参与者标记为已参与
        poll.participants[msg.sender] = true;
        // 将该参与者作为一个投票选项
        poll.options.push(toAsciiString(msg.sender));

        emit PollJoined(_pollId, msg.sender);
    }

    // 投票给别人
    function voteOthers(uint _pollId, uint _optionId) external {
        Poll storage poll = polls[_pollId];
        require(poll.isValid, "Poll is invalid");
        require(!poll.isVoted[msg.sender], "Already voted before");
        require(_optionId < poll.options.length, "Invalid option");

        // 检查自己是不是选项
        for(uint i = 0; i < poll.options.length; i ++){
            require(keccak256(abi.encodePacked(poll.options[i])) != keccak256(abi.encodePacked(toAsciiString(msg.sender))),
                "Voter is an option");
        }

        // 投票后修改变量值
        poll.voteNum[_optionId] ++;
        poll.totalVote ++;
        poll.isVoted[msg.sender] = true;

        emit PollVoted(_pollId, msg.sender, _optionId);
    }

    // 关闭某个投票
    function closePoll(uint _pollId) public {
        Poll storage poll = polls[_pollId];
        // 检查是否超时
        require(block.timestamp >= poll.endTime, "Poll is valid");

        // 超时，开始设置关闭
        poll.isValid = false;

        // 找到票数最大值
        uint maxVote = 0;
        for(uint i = 0; i < poll.options.length; i ++){
            if(poll.voteNum[i] > maxVote){
                maxVote = poll.voteNum[i];
            }
        }
        // 检查是不是都为0票
        bool allZero = true;
        if(maxVote > 0){
            allZero = false;
        }

        // 均为0票，奖励归还创建者
        if(allZero){
            payable(poll.creator).transfer(poll.reward);
        }
        else{
            // 寻找获胜者
            for(uint i = 0; i < poll.options.length; i ++){
                if(poll.voteNum[i] == maxVote){
                    poll.winner.push(parseAddr(poll.options[i]));
                }
            }
            // 获胜者平分奖励
            if(poll.winner.length > 0){
                uint256 winnerReward = poll.reward / poll.winner.length;
                for(uint i = 0; i < poll.winner.length; i ++){
                    payable(poll.winner[i]).transfer(winnerReward);
                }
            }
        }

        emit PollClosed(_pollId, poll.winner);
    }

    // 遍历，检查是否有要关闭的投票
    function closeEndPolls() public {
        for(uint i = 0; i < pollCount; i ++){
            if(polls[i].isValid && block.timestamp >= polls[i].endTime){
                closePoll(i);
            }
        }
    }

    // 获取所有已经结束的投票
    function getAllClosedPolls() public view returns (uint[] memory, string[] memory, string[][] memory) {
        uint closedCount = 0;
        uint[] memory closedIndexes = new uint[](pollCount);

        for(uint i = 0; i < pollCount; i++) {
            if (!polls[i].isValid) {
                closedIndexes[closedCount] = i;
                closedCount++;
            }
        }

        uint[] memory ids = new uint[](closedCount);
        string[] memory titles = new string[](closedCount);
        string[][] memory winnersList = new string[][](closedCount);

        for (uint i = 0; i < closedCount; i++) {
            uint pollId = closedIndexes[i];
            Poll storage poll = polls[pollId];
            ids[i] = pollId;
            titles[i] = poll.title;
            winnersList[i] = new string[](poll.winner.length);
            for (uint j = 0; j < poll.winner.length; j++) {
                winnersList[i][j] = toAsciiString(poll.winner[j]);
            }
        }

        return (ids, titles, winnersList);
    }

    function getOptions(uint _pollId) external view returns (string[] memory) {
        return polls[_pollId].options;
    }

    function getVoteNum(uint _pollId, uint _optionId) external view returns (uint) {
        return polls[_pollId].voteNum[_optionId];
    }

    function getWinners(uint _pollId) external view returns (address[] memory) {
        return polls[_pollId].winner;
    }

    function getJoinPrice(uint _pollId) external view returns (uint256) {
        return polls[_pollId].joinPrice;
    }

    function toAsciiString(address x) internal pure returns (string memory) {
        bytes memory s = new bytes(40);  // 地址的16进制表示
        for(uint i = 0; i < 20; i ++){
            bytes1 b = bytes1(uint8(uint(uint160(x)) / (2 ** (8 * (19 - i)))));
            bytes1 hi = bytes1(uint8(b) / 16);
            bytes1 lo = bytes1(uint8(b) - 16 * uint8(hi));
            s[2 * i] = char(hi);
            s[2 * i + 1] = char(lo);
        }
        return string(s);
    }

    function char(bytes1 b) internal pure returns (bytes1 c) {
        if (uint8(b) < 10) {
            return bytes1(uint8(b) + 0x30);  // 数字0到9
        }
        else{
            return bytes1(uint8(b) + 0x57);  // 字母'a'到'f'
        }
    }

    function parseAddr(string memory _a) internal pure returns (address) {
        bytes memory tmp = bytes(_a);
        uint160 iaddr = 0;
        uint160 b1;
        uint160 b2;
        for (uint i = 0; i < 40; i += 2) {
            iaddr *= 256;
            b1 = uint160(uint8(tmp[i]));
            b2 = uint160(uint8(tmp[i + 1]));
            if ((b1 >= 97) && (b1 <= 102)) b1 -= 87;
            else if ((b1 >= 48) && (b1 <= 57)) b1 -= 48;
            if ((b2 >= 97) && (b2 <= 102)) b2 -= 87;
            else if ((b2 >= 48) && (b2 <= 57)) b2 -= 48;
            iaddr += (b1 * 16 + b2);
        }
        return address(iaddr);
    }
}
