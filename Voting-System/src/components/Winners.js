import './Winners.css'
import React, { useEffect, useState } from 'react';

function Winners({ pollContract }) {
    const [closedPolls, setClosedPolls] = useState([]);

    useEffect(() => {
        async function fetchClosedPolls() {
            if (!pollContract) {
                console.log("Poll contract is not connected.");
                return;
            }
            try {
                // 首先关闭所有已结束的投票
                await pollContract.closeEndPolls();
                // 获取所有已关闭的投票
                const data = await pollContract.getAllClosedPolls();
                console.log("Received data from getAllClosedPolls:", data);

                const pollsData = data[0].map((idBigNumber, index) => {
                    return {
                        id: idBigNumber.toNumber(),
                        title: data[1][index],
                        winners: data[2][index]
                    };
                });

                setClosedPolls(pollsData);
            } catch (error) {
                console.error("Error fetching closed polls:", error);
            }
        }

        fetchClosedPolls();
    }, [pollContract]);

    return (
        <div>
            <h1>Closed Polls and Winners</h1>
            {closedPolls.map((poll) => (
                <div key={poll.id} className="closed-poll-container">
                    <h2 className="poll-title">{poll.title}</h2>
                    <h3>Winners:</h3>
                    <ul>
                        {poll.winners.map((winner, index) => (
                            <li key={index} className="winner-button">{winner}</li>
                        ))}
                    </ul>
                </div>
            ))}
        </div>
    );
}

export default Winners;
