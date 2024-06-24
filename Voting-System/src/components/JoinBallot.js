import './JoinBallot.css'
import {useEffect, useState} from 'react';

function JoinBallot({ pollContract }) {
    const [polls, setPolls] = useState([]);

    useEffect(() => {
        if(pollContract){
            updatePolls();
        }
    }, [pollContract]);

    async function updatePolls() {
        try {
            const closeTx = await pollContract.closeEndPolls();
            await closeTx.wait();
            await loadActivePolls();
        } catch (error) {
            console.error("Error updating polls:", error);
        }
    }

    async function loadActivePolls() {
        if (!pollContract) {
            console.log("Poll contract is not connected.");
            return <div>Loading contract...</div>;
        }

        try {
            const data = await pollContract.getAllPolls();
            console.log("Received data from getAllPolls:", data);

            const pollsDataPromises = data[0].map(async (idBigNumber, index) => {
                const id = idBigNumber.toNumber();
                const title = data[1][index];
                const options = data[2][index];

                const voteCountsPromises = options.map(async (option, optionIndex) => {
                    const countBigNumber = await pollContract.getVoteNum(id, optionIndex);
                    return countBigNumber.toNumber();
                });

                const voteCounts = await Promise.all(voteCountsPromises);

                return {
                    id,
                    title,
                    options,
                    voteCounts
                };
            });

            const resolvedPollsData = await Promise.all(pollsDataPromises);
            setPolls(resolvedPollsData);
        } catch (error) {
            console.error("Error fetching polls:", error);
        }
    }

    async function joinPoll(pollId) {
        try {
            const joinPriceWei = await pollContract.getJoinPrice(pollId);  // 参与投票费用
            const transaction = await pollContract.joinPoll(pollId, { value: joinPriceWei });
            await transaction.wait();
            alert('Successfully joined the poll!');
            await loadActivePolls();  // 重新加载投票列表显示更新
        } catch (error) {
            console.error('Error joining poll:', error);
            alert('Failed to join poll: ' + error.message);
        }
    }

    return (
        <div className="join-ballot-container">
            <h1>Join Active Polls</h1>
            {polls.map(poll => (
                <div key={poll.id} className="poll-container">
                    <div className="poll-title">
                        <h2>{poll.title}</h2>
                        <button className="poll-join-button" onClick={() => joinPoll(poll.id)}>Join This Poll</button>
                    </div>
                    <div>
                        {poll.options.map((option, index) => (
                            <div key={index} className="poll-option">
                                {option}
                            </div>
                        ))}
                    </div>
                </div>
            ))}
        </div>
    );
}

export default JoinBallot;
