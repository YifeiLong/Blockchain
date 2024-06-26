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
        address[] options;  // 投票选项
        bool isValid;  // 投票是否有效
        address[] winner;  // 获胜者地址数组
    }

    mapping(uint => Poll) public polls;  // 投票编号与投票的映射
    uint public pollCount;  // 当前投票个数

    // 创建新投票
    function createPoll(string memory _title, uint256 _endTime, uint256 _reward, uint256 _joinPrice,
        address[] memory _options) external payable {
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
    }

    // 获取当前所有正在进行中的投票
    function getAllPolls() public view returns (uint[] memory, string[] memory, address[][] memory) {
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
        address[][] memory optionsList = new address[][](activeCount);

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

        address creator = poll.creator;
        // 支付金额给投票发起者
        payable(creator).transfer(msg.value);
        // 将参与者标记为已参与
        poll.participants[msg.sender] = true;
        // 将该参与者作为一个投票选项
        poll.options.push(msg.sender);
    }

    // 投票给别人
    function voteOthers(uint _pollId, uint _optionId) external {
        Poll storage poll = polls[_pollId];
        require(poll.isValid, "Poll is invalid");
        require(!poll.isVoted[msg.sender], "Already voted before");
        require(_optionId < poll.options.length, "Invalid option");

        // 投票后修改变量值
        poll.voteNum[_optionId] ++;
        poll.totalVote ++;
        poll.isVoted[msg.sender] = true;
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
                    poll.winner.push(poll.options[i]);
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
    function getAllClosedPolls() public view returns (uint[] memory, string[] memory, address[][] memory) {
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
        address[][] memory winnersList = new string[][](closedCount);

        for (uint i = 0; i < closedCount; i++) {
            uint pollId = closedIndexes[i];
            Poll storage poll = polls[pollId];
            ids[i] = pollId;
            titles[i] = poll.title;
            winnersList[i] = new address[](poll.winner.length);
            for (uint j = 0; j < poll.winner.length; j++) {
                winnersList[i][j] = poll.winner[j];
            }
        }

        return (ids, titles, winnersList);
    }

    function getVoteNum(uint _pollId, uint _optionId) external view returns (uint) {
        return polls[_pollId].voteNum[_optionId];
    }

    function getJoinPrice(uint _pollId) external view returns (uint256) {
        return polls[_pollId].joinPrice;
    }
}
