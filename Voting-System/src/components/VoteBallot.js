import './VoteBallot.css'
import {useEffect, useState} from 'react';

function VoteBallot({ pollContract }) {
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
            return;
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

    async function vote(pollId, optionId) {
        if (pollContract) {
            try {
                const transaction = await pollContract.voteOthers(pollId, optionId);
                await transaction.wait();
                alert('Vote successfully recorded!');

                // 更新票数
                const updatedVoteCounts = await fetchVoteCounts(pollId, polls.find(p => p.id === pollId).options.length);
                setPolls(currentPolls =>
                    currentPolls.map(p => {
                        if (p.id === pollId) {
                            return { ...p, voteCounts: updatedVoteCounts };
                        }
                        return p;
                    })
                );
            } catch (error) {
                console.error("Error voting:", error);
                alert('Error voting: ' + error.message);
            }
        } else {
            alert("Smart contract not connected.");
        }
    }

    async function fetchVoteCounts(pollId, optionsCount) {
        let voteCounts = [];
        for (let i = 0; i < optionsCount; i++) {
            const count = await pollContract.getVoteNum(pollId, i);
            voteCounts.push(count.toNumber());
        }
        return voteCounts;
    }

    return (
        <div>
            <h1>Vote in Active Polls</h1>
            {polls.map((poll) => (
                <div key={poll.id} className="vote-poll-container">
                    <h2 className="vote-poll-title">{poll.title}</h2>
                    {poll.options.map((option, index) => (
                        <button key={index} className="vote-option-button"
                                onClick={() => vote(poll.id, index)}>
                            {option} ({poll.voteCounts[index]})
                        </button>
                    ))}
                </div>
            ))}
        </div>
    );
}

export default VoteBallot;
